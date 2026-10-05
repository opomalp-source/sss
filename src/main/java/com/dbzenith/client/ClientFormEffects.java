package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only aura particles, driven by each player's public state: powering up kicks a ring of dust outward, lifts
 * rocks off the ground and throws ki sparks upward; lightning forms crackle; calm (god-ki) forms shed slow sparkles.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientFormEffects {
    private ClientFormEffects() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        ClientLevel level = mc.level;
        RandomSource rnd = level.random;
        for (Player player : level.players()) {
            PublicStatePacket state = ClientPublicStates.get(player.getId());
            if (state == null || player.isInvisible()) continue;
            Form form = Forms.byId(state.form());
            if (form == Forms.GREAT_APE) continue;
            boolean powering = state.has(PublicStatePacket.CHARGING) || state.has(PublicStatePacket.HEAVY);
            double h = player.getBbHeight();
            int c = state.auraColor();

            if (powering) {
                boolean ownView = player == mc.player && mc.options.getCameraType().isFirstPerson();
                if (player.onGround()) {
                    // dust blown outward along the ground
                    if (player.tickCount % 3 == 0) {
                        for (int i = 0; i < 2; i++) {
                            double a = rnd.nextDouble() * Math.PI * 2;
                            level.addParticle(ParticleTypes.POOF, player.getX() + Math.cos(a) * 0.7, player.getY() + 0.05,
                                    player.getZ() + Math.sin(a) * 0.7, Math.cos(a) * 0.18, 0.005, Math.sin(a) * 0.18);
                        }
                    }
                    // pebbles of the ground drifting up
                    if (rnd.nextInt(3) == 0) {
                        BlockState below = level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ()));
                        if (below.getRenderShape() != RenderShape.INVISIBLE) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = (ownView ? 1.8 : 0.6) + rnd.nextDouble() * 1.4;
                            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below), player.getX() + Math.cos(a) * r,
                                    player.getY() + 0.1, player.getZ() + Math.sin(a) * r, 0, 0.35 + rnd.nextDouble() * 0.25, 0);
                        }
                    }
                }
                // ki sparks rising through the aura (not your own in first person: they would fill the camera)
                if (!ownView && rnd.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.END_ROD, player.getX() + (rnd.nextDouble() - 0.5) * 0.9,
                            player.getY() + rnd.nextDouble() * h * 0.6, player.getZ() + (rnd.nextDouble() - 0.5) * 0.9,
                            0, 0.08 + rnd.nextDouble() * 0.08, 0);
                }
                if (!ownView && rnd.nextInt(3) == 0) {
                    level.addParticle(new DustParticleOptions(com.dbzenith.client.fx.FxDraw.rgbVector(c), 1.2f),
                            player.getX() + (rnd.nextDouble() - 0.5) * 1.0, player.getY() + rnd.nextDouble() * h,
                            player.getZ() + (rnd.nextDouble() - 0.5) * 1.0, 0, 0.05, 0);
                }
            }
            if (form.calmAura() && rnd.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.END_ROD, player.getX() + (rnd.nextDouble() - 0.5) * 1.1,
                        player.getY() + rnd.nextDouble() * h, player.getZ() + (rnd.nextDouble() - 0.5) * 1.1, 0, 0.02, 0);
            }
            if (form.lightning() && rnd.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        player.getX() + (rnd.nextDouble() - 0.5) * 1.2, player.getY() + rnd.nextDouble() * h,
                        player.getZ() + (rnd.nextDouble() - 0.5) * 1.2,
                        (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3);
            }
        }
    }
}
