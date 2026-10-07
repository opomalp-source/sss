package com.dbzenith.skill;

import com.dbzenith.DBZenith;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Spirit Bomb's blast passes over its thrower and everyone who lent it energy (CX-17a). */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpiritBombEvents {
    private SpiritBombEvents() {}

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getDirectEntity() instanceof KiBlastEntity blast && blast.spares(event.getEntity())) event.setCanceled(true);
    }
}
