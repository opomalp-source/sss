package com.dbzenith.ki;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Burst movement. The client sends its movement input; the server validates cost/cooldown and sets velocity.
 * A short "afterimage" window afterwards makes entity attacks miss (see CombatEvents).
 */
public final class DashHandler {
    public static final String COOLDOWN_ID = "dash";

    private DashHandler() {}

    /** {@code forward}/{@code strafe} are the client's input impulses (-1..1; strafe positive = left). */
    public static boolean dash(ServerPlayer player, float forward, float strafe) {
        PlayerData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !player.isAlive() || player.isSpectator()) return false;
        DBZConfig.Server c = DBZConfig.SERVER;
        long now = player.level().getGameTime();
        if (com.dbzenith.combat.engine.Evasion.dashKey(player, data, forward, strafe)) return true;   // the dash key in context (CX-19)
        if (data.isOnCooldown(COOLDOWN_ID, now)) return false;
        if (com.dbzenith.registry.ModEffects.isStunned(player)) return false;
        boolean free = player.getAbilities().instabuild;
        if (!free && (data.getStamina() < c.dashStaminaCost.get() || data.getKi() < c.dashKiCost.get())) return false;

        forward = Mth.clamp(forward, -1, 1);
        strafe = Mth.clamp(strafe, -1, 1);
        if (Math.abs(forward) < 0.01f && Math.abs(strafe) < 0.01f) forward = 1; // no input: dash where you look

        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw)); // matches vanilla leftImpulse (positive = left)
        Vec3 dir = fwd.scale(forward).add(left.scale(strafe));
        if (data.isFlying() && player.getAbilities().flying && forward != 0) {
            dir = dir.add(0, -Mth.sin(player.getXRot() * Mth.DEG_TO_RAD) * forward, 0); // aim up/down while flying
        }
        double speed = c.dashStrength.get() * (1.0 + data.getDerived().moveSpeed());
        Vec3 v = dir.normalize().scale(speed);
        player.setDeltaMovement(v.x, player.onGround() ? Math.max(0.15, v.y) : v.y, v.z);
        player.hurtMarked = true; // pushes the velocity to the client
        com.dbzenith.network.ModNetwork.sendToTrackingAndSelf(player, new com.dbzenith.network.AnimEventPacket(player.getId(),
                com.dbzenith.network.AnimEventPacket.DASH, 0));

        if (!free) {
            data.setStamina(data.getStamina() - c.dashStaminaCost.get());
            data.setKi(data.getKi() - c.dashKiCost.get());
        }
        data.setCooldown(COOLDOWN_ID, now + c.dashCooldownTicks.get());
        data.setDashEvadeUntil(now + c.dashEvadeTicks.get());
        data.combat().lastDashTick = now;                                  // a blow right after lands as a Z-hit
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.6, 0.3, 0.05);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.DASH.get(),
                SoundSource.PLAYERS, 0.8f, 1.8f);
        return true;
    }
}
