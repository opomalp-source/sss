package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Air combat: hits on airborne targets do more damage and keep them aloft (juggling); a heavy hit from the ground
 * launches the target (knock-up); a heavy hit from above while looking down slams it into the ground (spike).
 * <p>
 * Vanilla applies its own knockback after the hurt event (and squashes upward speed for grounded targets), so the
 * new velocity is queued and applied at the end of the server tick.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AerialCombat {
    private record Pending(LivingEntity entity, Vec3 velocity) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private AerialCombat() {}

    public static boolean isAirborne(LivingEntity e) {
        return !e.onGround() && !e.isInWater() && !e.isPassenger() && !e.onClimbable();
    }

    /** Attacker in the air looking steeply down. */
    public static boolean isSpike(Player attacker) {
        return !attacker.onGround() && attacker.getXRot() > 30f;
    }

    static void afterHit(Player attacker, LivingEntity victim, boolean heavy, boolean victimAirborne) {
        DBZConfig.Server c = DBZConfig.SERVER;
        Vec3 look = attacker.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        if (heavy && isSpike(attacker)) {
            queue(victim, new Vec3(flat.x * 0.3, -c.spikeVelocity.get(), flat.z * 0.3));
        } else if (heavy && !victimAirborne) {
            queue(victim, new Vec3(flat.x * 0.2, c.knockUpVelocity.get(), flat.z * 0.2));
        } else if (heavy) {
            queue(victim, look.scale(1.6).add(0, 0.3, 0)); // batted away through the air
        } else if (victimAirborne) {
            queue(victim, new Vec3(flat.x * 0.15, c.airJuggleLift.get(), flat.z * 0.15));
        }
    }

    static void queue(LivingEntity e, Vec3 velocity) {
        PENDING.removeIf(p -> p.entity == e);
        PENDING.add(new Pending(e, velocity));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        for (Pending p : PENDING) {
            if (!p.entity.isAlive()) continue;
            p.entity.setDeltaMovement(p.velocity);
            if (p.velocity.y > 0) p.entity.fallDistance = 0;
            p.entity.hurtMarked = true; // sends the new motion, including to a player victim's own client
        }
        PENDING.clear();
    }
}
