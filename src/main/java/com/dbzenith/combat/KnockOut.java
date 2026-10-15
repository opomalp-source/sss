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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Knocked out (CX-51, reworked: the knocked-out rework). What would kill a fighter (a player, or an NPC fighter) puts
 * them down on the ground instead.
 * <ul>
 *   <li><b>Down:</b> they can't fight, fly, use ki or heal on their own. A strong one (Strength + Constitution at least
 *       {@code knockOutCrawlStrength}) can crawl; the rest just lie there. Duels keep their own knockout; creative
 *       players, /kill and the void still kill outright.</li>
 *   <li><b>The end</b> comes only two ways: their time runs out ({@code knockOutSeconds}; tougher fighters last up to
 *       half again longer; NPC fighters {@code knockOutNpcSeconds}), or someone grips them ({@link Grip}). Blows no
 *       longer finish them: each one shortens their time instead. A player can give up by holding sneak, which runs
 *       their time out.</li>
 *   <li><b>Saved:</b> a Senzu Bean (right-click them holding one) brings them back at full strength; healing them past a
 *       fifth of their health (Ki Heal, a Namekian's healing) gets them up; or a friend helps them up by holding
 *       right-click on them with an empty hand for five seconds (back up with a quarter of their health).</li>
 *   <li><b>Carried:</b> sneak and right-click them with an empty hand ({@link Carry}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class KnockOut {
    private static final UUID SLOW = UUID.fromString("c51d7a10-3e1b-4c5a-9e1d-0c51ab7e0001");
    /** Healed past this share of their health, the downed get back up. */
    private static final float REVIVE_AT = 0.2f;
    /** Helping someone up takes this long (ticks of holding right-click on them). */
    public static final int HELP_TICKS = 100;

    static final class Down {
        long until;
        int total;
        boolean crawl;
        boolean hadNoAi;
        int giveUp;
        boolean ending;
    }

    /** Someone helping a downed fighter up: who, how far along (ticks), when they last held on. */
    private static final class Help {
        LivingEntity victim;
        int progress;
        long last;
    }

    private static final Map<LivingEntity, Down> DOWN = new WeakHashMap<>();
    private static final Map<UUID, Help> HELPING = new HashMap<>();

    private KnockOut() {}

    /** Down on the ground right now (server side). */
    public static boolean isDown(LivingEntity e) {
        Down d = DOWN.get(e);
        return d != null && !d.ending;
    }

    /** Everyone down right now. */
    public static List<LivingEntity> downed() {
        List<LivingEntity> out = new ArrayList<>();
        for (Map.Entry<LivingEntity, Down> e : DOWN.entrySet()) if (!e.getValue().ending) out.add(e.getKey());
        return out;
    }

    /** Ticks left before this downed fighter bleeds out (-1 if not down). */
    public static int ticksLeft(LivingEntity e) {
        Down d = DOWN.get(e);
        return d == null || d.ending ? -1 : (int) Math.max(0, d.until - e.level().getGameTime());
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
        if (Carry.isCarrying(v)) Carry.drop(v, false);                        // whoever they carried falls with them
        Grip.breakFor(v);
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
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), com.dbzenith.registry.ModSounds.LAND.get(), SoundSource.PLAYERS, 1.2f, 0.7f);
        Blood.spill(v, v.position().add(0, v.getBbHeight() * 0.4, 0), 10);
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != v)
            attacker.displayClientMessage(Component.translatable("message.dbzenith.knocked_out_foe", v.getDisplayName()), true);
        broadcast(v, d);
    }

    /** They get back up (saved, healed, helped). */
    public static void standUp(LivingEntity v) {
        Down d = DOWN.remove(v);
        if (d == null) return;
        Carry.release(v);
        Grip.breakFor(v);
        restore(v, d);
        if (v instanceof ServerPlayer p) p.displayClientMessage(Component.translatable("message.dbzenith.revived"), true);
        broadcast(v, null);
    }

    /** Finished by {@code by} (as a grip does): they die by that fighter's hand. */
    public static void finishBy(LivingEntity v, LivingEntity by) {
        finish(v, ModDamageTypes.finished(v.level(), by));
    }

    /** Finished: gripped, bled out, or given up. */
    static void finish(LivingEntity v, DamageSource source) {
        Down d = DOWN.get(v);
        if (d == null || d.ending) return;
        d.ending = true;
        Carry.release(v);
        v.setHealth(0);
        v.die(source);                                                        // the death event lets them go (they are down)
        if (DOWN.containsKey(v)) end(v, d);
    }

    private static void end(LivingEntity v, Down d) {
        DOWN.remove(v);
        Carry.release(v);
        restore(v, d);
        broadcast(v, null);
    }

    private static void restore(LivingEntity v, Down d) {
        speed(v, 0);
        v.removeEffect(ModEffects.KI_SEAL.get());
        v.setNoGravity(false);
        if (v instanceof Mob m && !d.hadNoAi) m.setNoAi(false);
    }

    /** The source a bleed-out or a give-up dies of: whoever put them down, if anyone did. */
    private static DamageSource endSource(LivingEntity v) {
        return ModDamageTypes.bledOut(v.level(), v.getLastHurtByMob());
    }

    // ------------------------------------------------------------------ while down

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DOWN.isEmpty()) return;
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
            if (Grip.isGripped(v)) continue;                                  // in someone's hands: the grip decides
            if (v instanceof ServerPlayer p) {
                ModCapabilities.get(p).ifPresent(data -> {
                    if (data.isFlying()) com.dbzenith.ki.FlightHandler.stop(p, data);
                });
                if (p.isShiftKeyDown() && !Carry.isCarried(p)) {             // giving up: three seconds of sneaking runs the time out
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
        long now = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld().getGameTime();
        HELPING.values().removeIf(h -> now - h.last > 12 || !isDown(h.victim));
    }

    private static void speed(LivingEntity v, double amount) {
        AttributeInstance a = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (a.getModifier(SLOW) != null) a.removeModifier(SLOW);
        if (amount != 0) a.addTransientModifier(new AttributeModifier(SLOW, "dbzenith knocked out", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // ------------------------------------------------------------------ others: blows, saving, carrying

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide) return;
        Entity by = event.getSource().getEntity();
        if (by instanceof LivingEntity a && (isDown(a) || Carry.isCarrying(a)) && a != v) {   // the downed can't fight; nor can arms that are full
            event.setCanceled(true);
            return;
        }
        if (isDown(v) && by instanceof LivingEntity a && a != v) {
            event.setCanceled(true);                                          // a blow no longer finishes them: only a grip does
            Down d = DOWN.get(v);
            if (d == null || Grip.isGripped(v)) return;
            long now = v.level().getGameTime();
            d.until -= Math.max(40, d.total / 12);                             // but every blow costs them time
            Blood.spill(v, v.position().add(0, v.getBbHeight() * 0.35, 0), 6);
            v.level().playSound(null, v.getX(), v.getY(), v.getZ(), com.dbzenith.registry.ModSounds.PUNCH_HEAVY.get(), SoundSource.PLAYERS, 0.8f, 0.8f);
            if (d.until <= now) d.until = now + 1;
            broadcast(v, d);
            if (a instanceof ServerPlayer p) p.displayClientMessage(Component.translatable("message.dbzenith.grip_hint", v.getDisplayName()), true);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof LivingEntity v) || event.getLevel().isClientSide || !isDown(v)) return;
        Player helper = event.getEntity();
        if (isDown(helper) || Grip.isGripped(v)) return;
        ItemStack held = event.getItemStack();
        boolean done = feed(helper, v, held)
                || held.isEmpty() && helper.isShiftKeyDown() && Carry.pickUp(helper, v)
                || held.isEmpty() && !helper.isShiftKeyDown() && help(helper, v);
        if (done) {
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

    /**
     * Helping a downed fighter up: holding right-click on them with an empty hand (the game repeats the click while it
     * is held). Five seconds of it and they are back on their feet with a quarter of their health.
     */
    public static boolean help(Player helper, LivingEntity v) {
        if (!isDown(v) || Carry.isCarried(v) || helper.distanceTo(v) > 3.5) return false;
        long now = v.level().getGameTime();
        Help h = HELPING.get(helper.getUUID());
        if (h == null || h.victim != v || now - h.last > 12) {
            h = new Help();
            h.victim = v;
            h.last = now;
            HELPING.put(helper.getUUID(), h);
        }
        h.progress += (int) Math.max(1, now - h.last);
        h.last = now;
        if (h.progress >= HELP_TICKS) {
            HELPING.remove(helper.getUUID());
            revive(v, 0.25f);
            helper.displayClientMessage(Component.translatable("message.dbzenith.helped_up", v.getDisplayName()), true);
        } else {
            helper.displayClientMessage(Component.translatable("message.dbzenith.helping_up", v.getDisplayName(),
                    Math.min(99, h.progress * 100 / HELP_TICKS)), true);
            if (v instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.being_helped", helper.getDisplayName()), true);
        }
        return true;
    }

    /** Back up with {@code share} of their health. */
    public static void revive(LivingEntity v, float share) {
        if (!isDown(v)) return;
        if (v instanceof ServerPlayer p) ModCapabilities.get(p).ifPresent(data -> {
            data.setBody(data.getDerived().maxBody() * share);
            BodyHealth.mirror(p, data);
            data.markDirty();
        });
        v.setHealth(Math.max(v.getHealth(), v.getMaxHealth() * share));
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        standUp(v);
    }

    /** No healing on your own while down (food, regeneration): only a Senzu Bean or someone's help brings you round. */
    @SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (!event.getEntity().level().isClientSide && isDown(event.getEntity())) event.setCanceled(true);
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
