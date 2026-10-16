package com.dbzenith.transform;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.combat.KnockOut;
import com.dbzenith.combat.Limbs;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.combat.PvpRules;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Fighter;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.network.AnimEventPacket;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Great Apes attack through a grounded, telegraphed stomp, never humanoid melee or weapons. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class ApeStomp {
    public static final int CONTACT = 20, DURATION = 48, COOLDOWN = 56;
    private static final UUID PLANTED = UUID.fromString("166ed8d0-a05e-43fb-894a-cdbb54880b91");
    private static final Map<ServerPlayer, Action> ACTIONS = new WeakHashMap<>();
    private static ServerPlayer dealing;

    private static final class Action {
        long growUntil, started = -1, ready;
        Vec3 origin, foot;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        int side = 1;
        boolean struck;
    }

    private ApeStomp() {}

    public static boolean isApe(net.minecraft.world.entity.player.Player player) {
        return ModCapabilities.get(player).map(d -> GreatApe.isApeForm(d.getFormId())).orElse(false);
    }

    public static void entered(ServerPlayer player, Form from, Form to) {
        if (!GreatApe.isApeForm(to.id())) {
            ACTIONS.remove(player);
            plant(player, false);
        } else if (!GreatApe.isApeForm(from.id())) {
            Action a = new Action();
            a.growUntil = player.level().getGameTime() + ApeChange.END;
            ACTIONS.put(player, a);
            plant(player, true);
        }
    }

    /** Primary attack and heavy attack both request this same stomp, with or without a held item. */
    public static boolean press(ServerPlayer player) {
        if (!isApe(player) || !free(player) || !player.onGround() || PvpRules.inSafeZone(player)) return false;
        long now = player.level().getGameTime();
        Action a = ACTIONS.computeIfAbsent(player, p -> new Action());
        if (now < a.growUntil || now < a.ready || a.started >= 0) return false;
        var limbs = Limbs.of(player);
        int side = -a.side;
        if (!limbs.works(side > 0 ? Limbs.Limb.RIGHT_LEG : Limbs.Limb.LEFT_LEG)) side = -side;
        if (!limbs.works(side > 0 ? Limbs.Limb.RIGHT_LEG : Limbs.Limb.LEFT_LEG)) return false;
        Vec3 foot = foot(player, side);
        if (!supported(player, foot)) return false;
        var data = ModCapabilities.getOrThrow(player);
        double cost = Math.max(1, data.getDerived().maxStamina() * 0.06);
        if (!player.isCreative() && data.getStamina() < cost) return false;
        if (!player.isCreative()) data.setStamina(data.getStamina() - cost);
        a.started = now;
        a.ready = now + COOLDOWN;
        a.origin = player.position();
        a.dimension = player.level().dimension();
        a.foot = foot;
        a.side = side;
        a.struck = false;
        plant(player, true);
        ModNetwork.sendToTrackingAndSelf(player, new AnimEventPacket(player.getId(), AnimEventPacket.APE_STOMP, side > 0 ? 1 : 0));
        return true;
    }

    private static boolean free(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || KnockOut.isDown(p) || ModEffects.isStunned(p)) return false;
        var d = ModCapabilities.get(p).orElse(null);
        if (d == null || d.isMeditating() || d.isTransforming() || com.dbzenith.skill.BeamStruggle.isStruggling(p)) return false;
        var f = CombatEngine.peek(p);
        if (f == null) return true;
        Fighter.State state = f.state(p.level().getGameTime());
        return state != Fighter.State.STUNNED && state != Fighter.State.LAUNCHED && state != Fighter.State.KNOCKDOWN && state != Fighter.State.DEAD;
    }

    public static Vec3 foot(ServerPlayer p, int side) {
        double yaw = Math.toRadians(p.getYRot()), s = GreatApe.scaleOf(p);
        return p.position().add(-Math.cos(yaw) * side * 0.16 * s - Math.sin(yaw) * 0.1 * s, 0,
                -Math.sin(yaw) * side * 0.16 * s + Math.cos(yaw) * 0.1 * s);
    }

    private static boolean supported(ServerPlayer p, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.add(0, -0.2, 0));
        return !p.level().getBlockState(pos).getCollisionShape(p.level(), pos).isEmpty();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerPlayer p : new java.util.ArrayList<>(ACTIONS.keySet())) tick(p);
    }

    private static void tick(ServerPlayer p) {
        Action a = ACTIONS.get(p);
        if (a == null) return;
        long now = p.level().getGameTime();
        if (!isApe(p) || !p.isAlive() || p.isRemoved() || p.isSpectator()) {
            ACTIONS.remove(p);
            plant(p, false);
            return;
        }
        if (a.started >= 0) {
            if (!free(p) || !p.onGround() || p.level().dimension() != a.dimension || p.position().distanceToSqr(a.origin) > 2.25 || !supported(p, a.foot)) {
                a.started = -1;
                ModNetwork.sendToTrackingAndSelf(p, new AnimEventPacket(p.getId(), AnimEventPacket.STOP, 0));
            } else if (!a.struck && now - a.started >= CONTACT) {
                a.struck = true;
                Vec3 contact = foot(p, a.side);
                if (supported(p, contact)) impact(p, contact);
            } else if (now - a.started >= DURATION) a.started = -1;
        }
        plant(p, now < a.growUntil || a.started >= 0);
    }

    private static void plant(ServerPlayer p, boolean on) {
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (on && speed.getModifier(PLANTED) == null)
            speed.addTransientModifier(new AttributeModifier(PLANTED, "Great Ape planted feet", -1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (!on) speed.removeModifier(PLANTED);
    }

    private static void impact(ServerPlayer p, Vec3 at) {
        if (PvpRules.inSafeZone(p)) return;
        float radius = GreatApe.scaleOf(p) * 0.55f;
        ImpactPacket.at(at, new Vec3(0, 1, 0), ImpactPacket.APE_STOMP, radius, 0xB09C78, p.getId()).send(p.serverLevel());
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 4f, 0.35f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.5f);
        var data = ModCapabilities.getOrThrow(p);
        double raw = DamageCalculator.meleeOutgoing(data, 0, 0) * 2.2;
        AABB area = new AABB(at.x - radius, at.y - 1, at.z - radius, at.x + radius, at.y + 2.5, at.z + radius);
        for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != p && e.isAlive() && !e.isSpectator())) {
            if (!inRange(at, target.position(), radius) || p.isAlliedTo(target)) continue;
            Vec3 aim = target.position().add(0, 0.45, 0);
            var trace = p.level().clip(new ClipContext(at.add(0, 0.45, 0), aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (trace.getType() != HitResult.Type.MISS) continue;
            if (!PvpRules.mayAffect(p, target)) continue;
            double distance = Math.sqrt(target.position().subtract(at).horizontalDistanceSqr());
            float damage = (float) (raw * (1 - 0.45 * distance / radius));
            ServerPlayer old = dealing;
            int oldImpact = CombatEngine.pendingImpact;
            boolean hit;
            try {
                dealing = p;
                CombatEngine.pendingImpact = ImpactPacket.APE_STOMP;
                hit = target.hurt(ModDamageTypes.strike(p.level(), p), damage);
            } finally {
                dealing = old;
                CombatEngine.pendingImpact = oldImpact;
            }
            if (hit) {
                Vec3 out = target.position().subtract(at).multiply(1, 0, 1).normalize();
                target.push(out.x * 0.8, 0.3, out.z * 0.8);
                target.hurtMarked = true;
            }
        }
    }

    /** A ground wave reaches feet, not targets high overhead; it has a circular, bounded radius. */
    public static boolean inRange(Vec3 foot, Vec3 target, double radius) {
        double y = target.y - foot.y;
        return y >= -1 && y <= 1.5 && target.subtract(foot).horizontalDistanceSqr() <= radius * radius;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void vanillaAttack(AttackEntityEvent event) {
        if (!isApe(event.getEntity())) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer p) press(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onlyStomps(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && isApe(p) && dealing != p
                && !event.getSource().is(ModDamageTypes.BLED_OUT)) event.setCanceled(true);
    }
}
