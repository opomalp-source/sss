package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws a pulsing aura glow around players who are charging (seen by everyone, incl. yourself in third person). */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AuraRenderer {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(GLOW);

    private AuraRenderer() {}

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        boolean powering = state.has(PublicStatePacket.CHARGING) || state.has(PublicStatePacket.HEAVY);
        Form form = Forms.byId(state.form());
        boolean idle = !form.isBase() || state.overdrive() > 0; // transformed: a calmer, constant aura
        if (!powering && !idle) return;

        int c = state.auraColor();
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        float t = player.tickCount + event.getPartialTick();
        float strength = (0.6f + 0.4f * state.release() / 100f) * (powering ? 1f : 0.7f); // giant forms are already scaled by FormScaleRenderer
        float pulse = 1f + 0.06f * Mth.sin(t * 0.9f);
        int alpha = (int) (Math.min(1f, strength) * (powering ? 200 : 110));

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, player.getBbHeight() * 0.55, 0);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(TYPE);
        layer(pose, vc, 2.6f * pulse * strength, 3.9f * pulse * strength, r, g, b, alpha);  // outer flare
        layer(pose, vc, 1.5f * strength, 2.6f * strength, 255, 255, 255, (int) (alpha * 0.6f)); // bright core
        pose.popPose();
    }

    private static void layer(PoseStack pose, VertexConsumer vc, float w, float h, int r, int g, int b, int alpha) {
        pose.pushPose();
        pose.scale(w, h, 1f);
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        vertex(vc, m, n, -0.5f, -0.5f, 0, 1, r, g, b, alpha);
        vertex(vc, m, n, 0.5f, -0.5f, 1, 1, r, g, b, alpha);
        vertex(vc, m, n, 0.5f, 0.5f, 1, 0, r, g, b, alpha);
        vertex(vc, m, n, -0.5f, 0.5f, 0, 0, r, g, b, alpha);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int r, int g, int b, int a) {
        vc.vertex(m, x, y, 0).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }
}
