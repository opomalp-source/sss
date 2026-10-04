package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only ambient form effects (lightning sparks), driven by each player's public state. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientFormEffects {
    private ClientFormEffects() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        RandomSource rnd = mc.level.random;
        for (Player player : mc.level.players()) {
            PublicStatePacket state = ClientPublicStates.get(player.getId());
            if (state == null) continue;
            Form form = Forms.byId(state.form());
            if (form.lightning() && rnd.nextInt(3) == 0) {
                double h = player.getBbHeight();
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        player.getX() + (rnd.nextDouble() - 0.5) * 1.2, player.getY() + rnd.nextDouble() * h,
                        player.getZ() + (rnd.nextDouble() - 0.5) * 1.2,
                        (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3);
            }
        }
    }
}
