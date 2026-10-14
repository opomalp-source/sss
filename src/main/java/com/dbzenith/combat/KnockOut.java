package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.KnockOutPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.registry.ModItems;
import com.dbzenith.stats.Attribute;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Knocked out (CX-51, the user: "when the health bar drops to zero you don't die immediately, you are in a knocked state
 * on the ground; if you're strong you can still crawl around; you can be saved by other players with Senzu Beans, or
 * carried, and finished by other players").
 * <ul>
 *   <li>What would kill a fighter (a player, or an NPC fighter) puts them down on the ground instead, with the last
 *       sliver of health. Duels keep their own knockout (they cancel the death first); creative players, /kill and the
 *       void still kill outright.</li>
 *   <li>Down, they can't fight, fly, use ki or heal on their own. A strong one (Strength + Constitution at least
 *       {@code knockOutCrawlStrength}) can crawl slowly; the rest just lie there.</li>
 *   <li>They bleed out after {@code knockOutSeconds} (tougher fighters last up to half again longer; NPC fighters
 *       {@code knockOutNpcSeconds}). Any blow from someone finishes them at once. A player can give up by holding sneak.</li>
 *   <li>Saved: someone feeds them a Senzu Bean (right-click them holding one), or heals them back over a fifth of their
 *       health (Ki Heal, a Namekian's healing).</li>
 *   <li>Carried: sneak and right-click a downed fighter with an empty hand to pick them up over your shoulder (you walk
 *       slower); hold sneak for a second to put them down. They can't climb off.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class KnockOut {
    private static final UUID SLOW = UUID.fromString("c51d7a10-3e1b-4c5a-9e1d-0c51ab7e0001");
    private static final UUID CARRY_SLOW = UUID.fromString("c51d7a10-3e1b-4c5a-9e1d-0c51ab7e0002");
    /** Healed past this share of their health, the downed get back up. */
    private static final float REVIVE_AT = 0.2f;

    static final class Down {
        long until;
        int total;
        boolean crawl;
        boolean hadNoAi;
        int giveUp;
        boolean ending;
    }

    private static final Map<LivingEntity, Down> DOWN = new WeakHashMap<>();
    private static final Map<UUID, Integer> PUTTING_DOWN = new HashMap<>();
    private static boolean letGo;

    private KnockOut() {}

    /** Down on the ground right now (server side). */
    public static boolean isDown(LivingEntity e) {
        Down d = DOWN.get(e);
        return d != null && !d.ending;
    }

    /** Who can be knocked out instead of dying: players (not creative or spectating) and NPC fighters. */
    static boolean eligible(LivingEntity e) {
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e instanceof KiFighter;
    }

    // ------------------------------------------------------------------ going down, and the end

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide || !eligible(v)) return;
        Down d = DOWN.get(v);
        if (d != null) {                                                      // already down: this is the end
            end(v, d);
            return;
        }
        if (!DBZConfig.SERVER.knockOut.get() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
        knockOut(v, event.getSource());
    }

    public static void knockOut(LivingEntity v, DamageSource source) {
        long now = v.level().getGameTime();
        Down d = new Down();
        if (v instanceof ServerPlayer p) {
            PlayerData data = ModCapabilities.get(p).orElse(null);
            double tough = data == null ? 0 : data.getAttribute(Attribute.CONSTITUTION);
            d.total = (int) (DBZConfig.SERVER.knockOutSeconds.get() * 20 * (1 + Math.min(0.5, tough / 400.0)));
            d.crawl = data != null && data.getAttribute(Attribute.STRENGTH) + data.getAttribute(Attribute.CONSTITUTION) >= DBZConfig.SERVER.knockOutCrawlStrength.get();
            if (data != null) {
                if (data.isFlying()) com.dbzenith.ki.FlightHandler.stop(p, data);
                data.setCharging(false);
                com.dbzenith.skill.KiCharge.fizzle(p, false);
                com.dbzenith.transform.FormHandler.revertToBase(p, data);         // the form drops away with the last of the ki
                com.dbzenith.transform.Kaioken.stop(p, data, true);
                data.setBody(Math.max(1, data.getDerived().maxBody() * 0.01));
                BodyHealth.mirror(p, data);
                data.markDirty();
            }
            p.stopRiding();
            p.displayClientMessage(Component.translatable("message.dbzenith.knocked_out"), true);
        } else {
            d.total = DBZConfig.SERVER.knockOutNpcSeconds.get() * 20;
            if (v instanceof Mob m) {
                d.hadNoAi = m.isNoAi();
                m.setNoAi(true);
                m.setTarget(null);
                m.getNavigation().stop();
            }
        }
        v.setHealth(Math.max(1f, v.getMaxHealth() * 0.01f));
        v.setDeltaMovement(0, Math.min(0, v.getDeltaMovement().y), 0);
        v.clearFire();
        d.until = now + d.total;
        DOWN.put(v, d);
        speed(v, d.crawl ? -0.8 : -1.0);
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != v)
            attacker.displayClientMessage(Component.translatable("message.dbzenith.knocked_out_foe", v.getDisplayName()), true);
        broadcast(v, d);
    }

    /** They get back up (saved, healed). */
    public static void standUp(LivingEntity v) {
        Down d = DOWN.remove(v);
        if (d == null) return;
        restore(v, d);
        if (v instanceof ServerPlayer p) p.displayClientMessage(Component.translatable("message.dbzenith.revived"), true);
        broadcast(v, null);
    }

    /** Finished: by a blow, bleeding out, or giving up. */
    static void finish(LivingEntity v, DamageSource source) {
        Down d = DOWN.get(v);
        if (d == null || d.ending) return;
        d.ending = true;
        v.setHealth(0);
        v.die(source);                                                        // the death event lets them go (they are down)
        if (DOWN.containsKey(v)) end(v, d);
    }

    private static void end(LivingEntity v, Down d) {
        DOWN.remove(v);
        restore(v, d);
        broadcast(v, null);
    }

    private static void restore(LivingEntity v, Down d) {
        speed(v, 0);
        v.removeEffect(ModEffects.KI_SEAL.get());
        if (v instanceof Mob m && !d.hadNoAi) m.setNoAi(false);
        if (v.getVehicle() instanceof Player) {
            letGo = true;
            v.stopRiding();
            letGo = false;
        }
    }

    /** The source a bleed-out or a give-up dies of: whoever put them down, if anyone did. */
    private static DamageSource endSource(LivingEntity v) {
        LivingEntity last = v.getLastHurtByMob();
        if (last instanceof Player p) return v.damageSources().playerAttack(p);
        if (last != null) return v.damageSources().mobAttack(last);
        return v.damageSources().generic();
    }

    // ------------------------------------------------------------------ while down

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DOWN.isEmpty() && PUTTING_DOWN.isEmpty()) return;
        for (Map.Entry<LivingEntity, Down> e : new ArrayList<>(DOWN.entrySet())) {
            LivingEntity v = e.getKey();
            Down d = e.getValue();
            if (d.ending) continue;
            if (v.isRemoved() || !v.isAlive()) {
                DOWN.remove(v);
                continue;
            }
            long now = v.level().getGameTime();
            if (now % 20 == 0) v.addEffect(new MobEffectInstance(ModEffects.KI_SEAL.get(), 40, 0, false, false, false));
            if (v.getHealth() >= v.getMaxHealth() * REVIVE_AT) {               // healed back up
                standUp(v);
                continue;
            }
            if (v instanceof ServerPlayer p) {
                ModCapabilities.get(p).ifPresent(data -> {
                    if (data.isFlying()) com.dbzenith.ki.FlightHandler.stop(p, data);
                });
                if (p.isShiftKeyDown() && !(p.getVehicle() instanceof Player)) {   // giving up
                    if (++d.giveUp >= 60) {
                        finish(v, endSource(v));
                        continue;
                    }
                } else d.giveUp = 0;
            }
            if (now >= d.until) {
                finish(v, endSource(v));                                     // bled out
                continue;
            }
            if (now % 40 == 0) broadcast(v, d);                               // keep newcomers and the clock in step
        }
        // carriers: hold sneak a second to put them down; carrying slows you
        for (ServerPlayer p : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            boolean carrying = p.getPassengers().stream().anyMatch(r -> r instanceof LivingEntity l && isDown(l));
            AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                if (carrying && speed.getModifier(CARRY_SLOW) == null)
                    speed.addTransientModifier(new AttributeModifier(CARRY_SLOW, "dbzenith carrying", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL));
                else if (!carrying && speed.getModifier(CARRY_SLOW) != null) speed.removeModifier(CARRY_SLOW);
            }
            if (!carrying) {
                PUTTING_DOWN.remove(p.getUUID());
                continue;
            }
            int held = p.isShiftKeyDown() ? PUTTING_DOWN.merge(p.getUUID(), 1, Integer::sum) : 0;
            if (!p.isShiftKeyDown()) PUTTING_DOWN.remove(p.getUUID());
            if (held >= 20) {
                PUTTING_DOWN.remove(p.getUUID());
                letGo = true;
                for (Entity r : new ArrayList<>(p.getPassengers())) {
                    r.stopRiding();
                    r.setPos(p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2)));
                }
                letGo = false;
            }
        }
    }

    private static void speed(LivingEntity v, double amount) {
        AttributeInstance a = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (a.getModifier(SLOW) != null) a.removeModifier(SLOW);
        if (amount != 0) a.addTransientModifier(new AttributeModifier(SLOW, "dbzenith knocked out", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // ------------------------------------------------------------------ others: finish, save, carry

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide) return;
        Entity by = event.getSource().getEntity();
        if (by instanceof LivingEntity a && isDown(a) && a != v) {             // the downed can't fight
            event.setCanceled(true);
            return;
        }
        if (isDown(v) && by instanceof LivingEntity a && a != v) {
            event.setCanceled(true);                                          // a blow while down finishes them
            finish(v, event.getSource());
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof LivingEntity v) || event.getLevel().isClientSide || !isDown(v)) return;
        Player helper = event.getEntity();
        ItemStack held = event.getItemStack();
        if (feed(helper, v, held) || held.isEmpty() && helper.isShiftKeyDown() && carry(helper, v)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** A Senzu Bean fed to a downed fighter: they are up again at full strength. */
    public static boolean feed(Player helper, LivingEntity v, ItemStack held) {
        if (!isDown(v) || !held.is(ModItems.SENZU_BEAN.get())) return false;
        v.setHealth(v.getMaxHealth());
        if (v instanceof Player p) ModCapabilities.get(p).ifPresent(PlayerData::refill);
        Limbs.mend(v);
        if (!helper.getAbilities().instabuild) held.shrink(1);
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1f, 1.1f);
        standUp(v);
        helper.displayClientMessage(Component.translatable("message.dbzenith.saved", v.getDisplayName()), true);
        return true;
    }

    /** Picks a downed fighter up over the shoulder. */
    public static boolean carry(Player helper, LivingEntity v) {
        if (!isDown(v) || !helper.getPassengers().isEmpty() || v.getVehicle() != null) return false;
        v.startRiding(helper, true);                                      // over the shoulder
        helper.displayClientMessage(Component.translatable("message.dbzenith.carrying", v.getDisplayName()), true);
        return true;
    }

    /** No healing on your own while down (food, regeneration): only a Senzu Bean or someone's healing brings you round. */
    @SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (!event.getEntity().level().isClientSide && isDown(event.getEntity())) event.setCanceled(true);
    }

    /** The carried can't climb off; only putting them down (or getting up) lets them go. */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (letGo || !event.isDismounting() || event.getLevel().isClientSide) return;
        if (event.getEntityMounting() instanceof LivingEntity v && isDown(v) && event.getEntityBeingMounted() instanceof Player)
            event.setCanceled(true);
    }

    /** Someone starts seeing a downed fighter: tell them. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity v && event.getEntity() instanceof ServerPlayer p) {
            Down d = DOWN.get(v);
            if (d != null && !d.ending) ModNetwork.sendTo(p, packet(v, d));
        }
    }

    /** Logging out while down is giving up. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isDown(p)) finish(p, endSource(p));
    }

    private static KnockOutPacket packet(LivingEntity v, Down d) {
        if (d == null) return new KnockOutPacket(v.getId(), -1, 0, false);
        return new KnockOutPacket(v.getId(), (int) Math.max(0, d.until - v.level().getGameTime()), d.total, d.crawl);
    }

    private static void broadcast(LivingEntity v, Down d) {
        KnockOutPacket m = packet(v, d);
        if (v instanceof ServerPlayer p) ModNetwork.sendToTrackingAndSelf(p, m);
        else ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> v), m);
    }
}
