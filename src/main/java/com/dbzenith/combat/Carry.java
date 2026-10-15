package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.network.CarryPacket;
import com.dbzenith.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carrying the downed (the knocked-out rework). Vanilla riding is not used. A player riding a player is something
 * Minecraft handles badly: the carried player's own game could climb off on its own while the server held them on, so
 * on their screen nothing happened and the carry sometimes failed outright. Instead the carried fighter is placed on
 * the carrier's shoulder every tick by the server, and by every client from the same rule ({@link #shoulder}), so all
 * screens agree, the carried player's included.
 * <ul>
 *   <li><b>Picking up:</b> sneak and right-click a downed fighter with an empty hand.</li>
 *   <li><b>Carrying:</b> you walk 30% slower and can't sprint. You can fly, but more slowly. You can't attack or use ki
 *       with your arms full.</li>
 *   <li><b>Letting go:</b> hold sneak for a second to put them down gently in front of you, or attack to throw them.
 *       Being knocked down or stunned, or going too far from them (a teleport), drops them.</li>
 * </ul>
 * Carrying doesn't stop their bleeding: carry them to someone who can save them.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class Carry {
    private static final UUID SLOW = UUID.fromString("c51d7a10-3e1b-4c5a-9e1d-0c51ab7e0002");
    private static final Map<LivingEntity, LivingEntity> CARRIED_BY = new HashMap<>();   // carried -> carrier
    private static final Map<UUID, Integer> PUTTING_DOWN = new HashMap<>();

    private Carry() {}

    /** Who is carrying this fighter (null if no one). */
    public static LivingEntity carrierOf(LivingEntity carried) {
        return CARRIED_BY.get(carried);
    }

    /** Who this fighter is carrying (null if no one). */
    public static LivingEntity carriedBy(LivingEntity carrier) {
        for (Map.Entry<LivingEntity, LivingEntity> e : CARRIED_BY.entrySet()) if (e.getValue() == carrier) return e.getKey();
        return null;
    }

    public static boolean isCarrying(LivingEntity carrier) {
        return CARRIED_BY.containsValue(carrier);
    }

    public static boolean isCarried(LivingEntity e) {
        return CARRIED_BY.containsKey(e);
    }

    /**
     * Where a carried fighter goes: over the carrier's right shoulder, the waist on it, lying across it. Feet position
     * and yaw; both sides use this, so the server and every screen agree.
     */
    public static Vec3 shoulder(double x, double y, double z, float carrierYaw, float carrierHeight) {
        double r = Math.toRadians(carrierYaw);
        double rightX = -Math.cos(r), rightZ = -Math.sin(r);
        double s = carrierHeight / 1.8;
        return new Vec3(x + rightX * 0.3 * s, y + 0.78 * s, z + rightZ * 0.3 * s);   // the body pivots 0.7 above its feet: tipped face down, the waist sits on the shoulder
    }

    /** Picks a downed fighter up over the shoulder. */
    public static boolean pickUp(LivingEntity carrier, LivingEntity v) {
        if (!KnockOut.isDown(v) || KnockOut.isDown(carrier) || isCarrying(carrier) || isCarried(v) || isCarried(carrier)) return false;
        if (carrier == v || carrier.distanceTo(v) > 4) return false;
        if (Grip.isGripping(carrier) || Grip.isGripped(v)) return false;
        v.stopRiding();
        CARRIED_BY.put(v, carrier);
        v.setNoGravity(true);
        v.fallDistance = 0;
        slow(carrier, true);
        place(v, carrier);
        carrier.level().playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1f, 0.7f);
        if (carrier instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.carrying", v.getDisplayName()), true);
        if (v instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.carried_by", carrier.getDisplayName()), true);
        broadcast(carrier, v, true);
        return true;
    }

    /** Lets go: gently in front of the carrier, or thrown ({@code throwIt}). */
    public static void drop(LivingEntity carrier, boolean throwIt) {
        LivingEntity v = carriedBy(carrier);
        if (v == null) return;
        CARRIED_BY.remove(v);
        v.setNoGravity(false);
        slow(carrier, false);
        Vec3 look = carrier.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 at = carrier.position().add(look.scale(throwIt ? 0.9 : 1.1));
        if (!carrier.level().noCollision(v, v.getDimensions(v.getPose()).makeBoundingBox(at))) at = carrier.position();
        v.teleportTo(at.x, at.y + (throwIt ? 0.6 : 0.05), at.z);
        if (throwIt) {
            v.setDeltaMovement(look.x * 0.9, 0.35, look.z * 0.9);
            v.hurtMarked = true;
            carrier.level().playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), com.dbzenith.registry.ModSounds.WHOOSH.get(), SoundSource.PLAYERS, 1f, 0.8f);
        } else {
            carrier.level().playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
        if (v instanceof ServerPlayer p) p.connection.teleport(at.x, at.y + (throwIt ? 0.6 : 0.05), at.z, p.getYRot(), p.getXRot());
        broadcast(carrier, v, false);
    }

    /** The carried is free of whoever had them (they got up, died, were gripped...). */
    public static void release(LivingEntity v) {
        LivingEntity carrier = CARRIED_BY.get(v);
        if (carrier == null) return;
        CARRIED_BY.remove(v);
        v.setNoGravity(false);
        slow(carrier, false);
        broadcast(carrier, v, false);
    }

    private static void place(LivingEntity v, LivingEntity carrier) {
        Vec3 at = shoulder(carrier.getX(), carrier.getY(), carrier.getZ(), carrier.getYRot(), carrier.getBbHeight());
        v.setPos(at.x, at.y, at.z);
        v.setYRot(carrier.getYRot() + 180);                                     // head down the carrier's back, legs in front
        v.yBodyRot = carrier.getYRot() + 180;
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0;
    }

    private static void slow(LivingEntity carrier, boolean on) {
        AttributeInstance speed = carrier.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(SLOW) != null;
        if (on && !has) speed.addTransientModifier(new AttributeModifier(SLOW, "dbzenith carrying", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!on && has) speed.removeModifier(SLOW);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CARRIED_BY.isEmpty()) return;
        for (Map.Entry<LivingEntity, LivingEntity> e : new ArrayList<>(CARRIED_BY.entrySet())) {
            LivingEntity v = e.getKey(), carrier = e.getValue();
            boolean gone = v.isRemoved() || !v.isAlive() || carrier.isRemoved() || !carrier.isAlive() || v.level() != carrier.level()
                    || !KnockOut.isDown(v);
            if (gone) {
                release(v);
                continue;
            }
            if (KnockOut.isDown(carrier) || com.dbzenith.registry.ModEffects.isStunned(carrier) || v.distanceToSqr(carrier) > 36) {
                drop(carrier, false);                                            // knocked down, stunned, or teleported away: they fall
                continue;
            }
            place(v, carrier);
            if (carrier instanceof Player p) {
                if (p.isSprinting()) p.setSprinting(false);
                int held = p.isShiftKeyDown() ? PUTTING_DOWN.merge(p.getUUID(), 1, Integer::sum) : 0;
                if (!p.isShiftKeyDown()) PUTTING_DOWN.remove(p.getUUID());
                if (held >= 20) {                                                // a second of sneaking: put them down
                    PUTTING_DOWN.remove(p.getUUID());
                    drop(p, false);
                }
            }
        }
    }

    /** Attacking with your arms full throws whoever you carry (from the client's swing). */
    public static void attacked(ServerPlayer p) {
        if (isCarrying(p)) drop(p, true);
    }

    private static CarryPacket packet(LivingEntity carrier, LivingEntity v, boolean on) {
        return new CarryPacket(carrier.getId(), v.getId(), on);
    }

    private static void broadcast(LivingEntity carrier, LivingEntity v, boolean on) {
        CarryPacket m = packet(carrier, v, on);
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> carrier), m);
        if (v instanceof ServerPlayer p) ModNetwork.sendTo(p, m);                // the carried, wherever they are
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher) || !(event.getTarget() instanceof LivingEntity t)) return;
        LivingEntity carrier = CARRIED_BY.get(t);
        if (carrier != null) ModNetwork.sendTo(watcher, packet(carrier, t, true));
        LivingEntity carried = carriedBy(t);
        if (carried != null) ModNetwork.sendTo(watcher, packet(t, carried, true));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LivingEntity p = event.getEntity();
        if (isCarrying(p)) drop(p, false);
        release(p);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        LivingEntity p = event.getEntity();
        if (isCarrying(p)) {
            LivingEntity v = carriedBy(p);
            if (v != null) release(v);
        }
        release(p);
    }

    /** Clears it all (server stopping). */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        CARRIED_BY.clear();
        PUTTING_DOWN.clear();
    }

    /** Is this the entity someone is carrying (for vanilla's checks)? */
    static boolean held(Entity e) {
        return e instanceof LivingEntity l && isCarried(l);
    }
}
