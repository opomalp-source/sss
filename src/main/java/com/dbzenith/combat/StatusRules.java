package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.registry.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stun: no melee hits and no jumping (movement itself is stopped by the effect's attribute modifier).
 * The other stun and ki-seal rules live where the action happens (techniques, flight, charging, dash, transform).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StatusRules {
    private StatusRules() {}

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getDirectEntity() instanceof LivingEntity attacker
                && attacker == event.getSource().getEntity() && ModEffects.isStunned(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (ModEffects.isStunned(e)) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x, Math.min(0, v.y), v.z);
        }
    }
}
