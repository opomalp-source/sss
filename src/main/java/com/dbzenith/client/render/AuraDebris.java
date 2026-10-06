package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What a charge does to the ground (CX-11a): dust blown out from the feet, and chunks of the ground itself (textured
 * from the block they came from) lifting off, spinning slowly as they rise round the fighter, then crumbling away.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AuraDebris {
    private static final class Rock {
        double x, y, z, vy;
        float rx, ry, spinX, spinY, size;
        int age, life;
        BlockState block;
        BlockPos from;
    }

    private static final List<Rock> ROCKS = new ArrayList<>();
    private static final int MAX = 160;

    private AuraDebris() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ROCKS.clear();
            return;
        }
        if (mc.isPaused()) return;
        int detail = com.dbzenith.config.DBZConfig.CLIENT.auraDetail.get();
        RandomSource rnd = mc.level.random;
        for (Player p : mc.level.players()) {
            PublicStatePacket state = ClientPublicStates.get(p.getId());
            if (state == null || !state.powering() || !p.onGround() || p.isInvisible()) continue;
            BlockPos under = p.blockPosition().below();
            BlockState ground = mc.level.getBlockState(under);
            if (!ground.isAir()) {                                                   // dust blown out from the feet
                for (int i = 0; i < 2 + detail; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, r = 0.4 + rnd.nextDouble() * 0.4;
                    mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.getX() + Math.cos(a) * r, p.getY() + 0.05,
                            p.getZ() + Math.sin(a) * r, Math.cos(a) * 0.3, 0.06, Math.sin(a) * 0.3);
                }
                if (p.tickCount % 8 == 0) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    mc.level.addParticle(ParticleTypes.CLOUD, p.getX() + Math.cos(a) * 1.1, p.getY() + 0.1, p.getZ() + Math.sin(a) * 1.1,
                            Math.cos(a) * 0.08, 0.01, Math.sin(a) * 0.08);
                }
            }
            if (detail > 0 && ROCKS.size() < MAX && rnd.nextFloat() < 0.3f + 0.15f * detail) {   // a chunk of the ground lifts off
                double a = rnd.nextDouble() * Math.PI * 2, r = 1.0 + rnd.nextDouble() * 1.8;
                BlockPos at = BlockPos.containing(p.getX() + Math.cos(a) * r, p.getY() - 0.5, p.getZ() + Math.sin(a) * r);
                BlockState b = mc.level.getBlockState(at);
                if (b.isAir() || !b.isSolidRender(mc.level, at)) continue;
                Rock rock = new Rock();
                rock.x = p.getX() + Math.cos(a) * r;
                rock.y = at.getY() + 1.02;
                rock.z = p.getZ() + Math.sin(a) * r;
                rock.vy = 0.025 + rnd.nextDouble() * 0.04;
                rock.spinX = (rnd.nextFloat() - 0.5f) * 8;
                rock.spinY = (rnd.nextFloat() - 0.5f) * 8;
                rock.size = 0.08f + rnd.nextFloat() * 0.14f;
                rock.life = 50 + rnd.nextInt(40);
                rock.block = b;
                rock.from = at;
                ROCKS.add(rock);
            }
        }
        for (Iterator<Rock> it = ROCKS.iterator(); it.hasNext(); ) {
            Rock r = it.next();
            r.age++;
            r.y += r.vy;
            r.vy *= 0.975;
            r.rx += r.spinX;
            r.ry += r.spinY;
            if (r.age >= r.life) it.remove();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ROCKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer vc = buffers.getBuffer(type);
        for (Rock r : ROCKS) {
            float shrink = Mth.clamp((r.life - r.age - partial) / 12f, 0, 1) * Mth.clamp((r.age + partial) / 4f, 0, 1);
            if (shrink <= 0) continue;
            TextureAtlasSprite sprite = mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(r.block);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(r.x, r.y, r.z));
            pose.pushPose();
            pose.translate(r.x - cam.x, r.y + r.vy * partial - cam.y, r.z - cam.z);
            pose.mulPose(Axis.XP.rotationDegrees(r.rx + r.spinX * partial));
            pose.mulPose(Axis.YP.rotationDegrees(r.ry + r.spinY * partial));
            float s = r.size * shrink;
            pose.scale(s, s, s);
            cube(pose, vc, sprite, light);
            pose.popPose();
        }
        buffers.endBatch(type);
    }

    /** A unit cube centred on the origin, every face showing part of the block's texture. */
    private static void cube(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sp, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float u0 = sp.getU(2), u1 = sp.getU(10), v0 = sp.getV(2), v1 = sp.getV(10);
        float[][][] faces = {
                {{-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, .5f, .5f}, {-.5f, .5f, .5f}, {0, 0, 1}},
                {{.5f, -.5f, -.5f}, {-.5f, -.5f, -.5f}, {-.5f, .5f, -.5f}, {.5f, .5f, -.5f}, {0, 0, -1}},
                {{.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}, {.5f, .5f, .5f}, {1, 0, 0}},
                {{-.5f, -.5f, -.5f}, {-.5f, -.5f, .5f}, {-.5f, .5f, .5f}, {-.5f, .5f, -.5f}, {-1, 0, 0}},
                {{-.5f, .5f, .5f}, {.5f, .5f, .5f}, {.5f, .5f, -.5f}, {-.5f, .5f, -.5f}, {0, 1, 0}},
                {{-.5f, -.5f, -.5f}, {.5f, -.5f, -.5f}, {.5f, -.5f, .5f}, {-.5f, -.5f, .5f}, {0, -1, 0}}};
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (float[][] f : faces) {
            for (int k = 0; k < 4; k++) {
                vc.vertex(m, f[k][0], f[k][1], f[k][2]).color(255, 255, 255, 255).uv(uv[k][0], uv[k][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, f[4][0], f[4][1], f[4][2]).endVertex();
            }
        }
    }
}
