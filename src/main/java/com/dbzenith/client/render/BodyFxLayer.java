package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Power you can see on the body itself: god ki glows along the edges of the body in the aura's colour (silver for
 * the silver forms), Kaioken flushes the whole body red with each heartbeat, and lightning forms have bolts crawling
 * over them.
 */
public class BodyFxLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form/body_glow.png");
    private static final ResourceLocation MASK = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form/body_mask.png");
    private static final ResourceLocation[] SPARKS = new ResourceLocation[4];

    static {
        for (int i = 0; i < 4; i++) SPARKS[i] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form/sparks_" + i + ".png");
    }

    public BodyFxLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        Form form = FormHairLayer.flicker(player, state, ageInTicks);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
        float t = ageInTicks;
        if (!form.isBase() && form.calmAura()) {                                      // god ki: the body's edges glow
            float pulse = 0.4f + 0.12f * Mth.sin(t * 0.12f);
            int c = form.auraColor();
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, overlay,
                    r(c) * pulse, g(c) * pulse, b(c) * pulse, 1f);
        }
        if (state.has(PublicStatePacket.KAIOKEN)) {                                   // Kaioken: a flush with every heartbeat
            float beat = 0.22f + 0.12f * Math.max(0, Mth.sin(t * 0.45f));
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(MASK)), light, overlay, 1f, 0.12f, 0.08f, beat);
        }
        if (form.lightning()) {                                                       // bolts crawling over the body, now and then
            int step = (int) (t / 2);
            long h = (player.getId() * 31L + step) * 2654435761L;
            if (((h >>> 16) & 255) < 110) {
                int c = form.auraColor();
                float w = 0.55f;
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(SPARKS[step & 3])), LightTexture.FULL_BRIGHT, overlay,
                        Mth.lerp(w, r(c), 1f), Mth.lerp(w, g(c), 1f), Mth.lerp(w, b(c), 1f), 1f);
            }
        }
    }

    private static float r(int c) { return ((c >> 16) & 255) / 255f; }
    private static float g(int c) { return ((c >> 8) & 255) / 255f; }
    private static float b(int c) { return (c & 255) / 255f; }
}
