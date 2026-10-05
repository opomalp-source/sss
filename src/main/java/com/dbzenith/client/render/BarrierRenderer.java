package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.fx.FxDraw;
import com.dbzenith.client.fx.FxRenderTypes;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Ki Barrier: a sphere of ki around the fighter, nearly clear where you look straight through it and bright at
 * the rim, with a band of light rolling over it.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BarrierRenderer {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final int LAT = 14, LON = 24;
    private static final int COLOR = 0x60C0FF;

    private BarrierRenderer() {}

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (com.dbzenith.client.ui.PortraitRenderer.isDrawing()) return;
        Player player = event.getEntity();
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null || !state.has(PublicStatePacket.BARRIER) || player.isInvisible()) return;
        float partial = event.getPartialTick();
        float t = player.tickCount + partial;
        Minecraft mc = Minecraft.getInstance();
        Vec3 feet = new Vec3(Mth.lerp(partial, player.xo, player.getX()), Mth.lerp(partial, player.yo, player.getY()),
                Mth.lerp(partial, player.zo, player.getZ()));
        Vec3 center = new Vec3(0, 1.0, 0);
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition().subtract(feet);
        float radius = 1.45f + 0.03f * Mth.sin(t * 0.4f);
        boolean inside = eye.subtract(center).length() < radius;

        PoseStack pose = event.getPoseStack();
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(FxRenderTypes.additive(GLOW));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float band = (t * 0.03f) % 1f;                                  // a ring of light rolling up the sphere
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                Vec3[] p = new Vec3[4];
                int[] a = new int[4];
                for (int k = 0; k < 4; k++) {
                    int la = i + (k == 2 || k == 3 ? 1 : 0), lo = j + (k == 1 || k == 2 ? 1 : 0);
                    double theta = Math.PI * la / LAT, phi = 2 * Math.PI * lo / LON;
                    Vec3 normal = new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
                    p[k] = center.add(normal.scale(radius));
                    double facing = Math.abs(normal.dot(eye.subtract(p[k]).normalize()));
                    double rim = inside ? 0.35 : Math.pow(1 - facing, 2.2);
                    double h = 1 - (double) la / LAT;
                    double glow = Math.max(0, 1 - Math.abs(h - band) * 9);
                    a[k] = (int) (20 + 150 * rim + 90 * glow);
                }
                FxDraw.vertex(vc, m, n, (float) p[0].x, (float) p[0].y, (float) p[0].z, 0.5f, 0.5f, COLOR, a[0], LightTexture.FULL_BRIGHT);
                FxDraw.vertex(vc, m, n, (float) p[1].x, (float) p[1].y, (float) p[1].z, 0.5f, 0.5f, COLOR, a[1], LightTexture.FULL_BRIGHT);
                FxDraw.vertex(vc, m, n, (float) p[2].x, (float) p[2].y, (float) p[2].z, 0.5f, 0.5f, COLOR, a[2], LightTexture.FULL_BRIGHT);
                FxDraw.vertex(vc, m, n, (float) p[3].x, (float) p[3].y, (float) p[3].z, 0.5f, 0.5f, COLOR, a[3], LightTexture.FULL_BRIGHT);
            }
        }
    }
}
