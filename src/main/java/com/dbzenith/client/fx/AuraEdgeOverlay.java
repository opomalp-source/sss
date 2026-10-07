package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * First person you cannot see your own aura, so it burns in around the screen edge instead: strongly while powering
 * up, faintly while a form is held. Also draws {@link CameraFx}'s full-screen flash (transformations, close blasts).
 */
public final class AuraEdgeOverlay implements IGuiOverlay {
    private static final ResourceLocation EDGE = new ResourceLocation(DBZenith.MOD_ID, "textures/gui/aura_edge.png");

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        boolean enabled = DBZConfig.CLIENT.firstPersonAura.get();

        if (enabled && mc.options.getCameraType().isFirstPerson()) {
            PublicStatePacket state = ClientPublicStates.get(mc.player.getId());
            if (state != null) {
                boolean powering = state.powering();
                Form form = Forms.byId(state.form());
                boolean held = !form.isBase() || state.has(PublicStatePacket.KAIOKEN);
                if ((powering || held) && form != Forms.GREAT_APE) {
                    float t = mc.player.tickCount + partial;
                    float base = powering ? 0.8f : 0.28f;
                    int c = state.auraColor();
                    float r = ((c >> 16) & 255) / 255f, gr = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    g.setColor(r, gr, b, base * (0.85f + 0.15f * Mth.sin(t * 0.9f)));
                    g.blit(EDGE, 0, 0, width, height, 0, 0, 128, 128, 128, 128);
                    int grow = (int) (6 + 4 * Mth.sin(t * 0.55f));            // a second, shifting layer reads as flicker
                    g.setColor(r, gr, b, base * 0.6f * (0.7f + 0.3f * Mth.sin(t * 1.3f + 1)));
                    g.blit(EDGE, -grow, -grow * 2, width + grow * 2, height + grow * 3, 0, 0, 128, 128, 128, 128);
                    g.setColor(1, 1, 1, 1);
                    RenderSystem.disableBlend();
                }
            }
        }

        float flash = CameraFx.flashAlpha(partial);
        if (flash > 0.01f && enabled) {
            int a = (int) (Mth.clamp(flash, 0, 1) * 0.85f * 255);
            g.fill(0, 0, width, height, a << 24 | CameraFx.flashColor());
        }
        float lines = CameraFx.speedLinesAlpha(partial);
        if (lines > 0.01f) speedLines(g, width, height, lines, CameraFx.speedLinesColor(), CameraFx.speedLinesSeed());
    }

    /**
     * Speed lines (CX-19e): thin wedges from the screen's edges toward the middle, stopping short of it, in a colour,
     * fading with {@code alpha}. The same seed gives the same lines, so a burst holds still while it fades.
     */
    static void speedLines(GuiGraphics g, int width, int height, float alpha, int rgb, int seed) {
        com.mojang.blaze3d.vertex.Tesselator tess = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder buf = tess.getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        buf.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        org.joml.Matrix4f m = g.pose().last().pose();
        float cx = width / 2f, cy = height / 2f, far = (float) Math.hypot(cx, cy) * 1.05f;
        int r = (rgb >> 16) & 255, gr = (rgb >> 8) & 255, b = rgb & 255;
        java.util.Random rnd = new java.util.Random(seed * 31L + 7);
        for (int i = 0; i < 44; i++) {
            float ang = (float) (rnd.nextDouble() * Math.PI * 2), half = 0.004f + rnd.nextFloat() * 0.012f;
            float inner = far * (0.42f + rnd.nextFloat() * 0.25f + (1 - alpha) * 0.2f);
            int a = (int) (Mth.clamp(alpha * (0.45f + rnd.nextFloat() * 0.5f), 0, 1) * 255);
            float x0 = cx + Mth.cos(ang) * inner, y0 = cy + Mth.sin(ang) * inner;
            float x1 = cx + Mth.cos(ang - half) * far, y1 = cy + Mth.sin(ang - half) * far;
            float x2 = cx + Mth.cos(ang + half) * far, y2 = cy + Mth.sin(ang + half) * far;
            buf.vertex(m, x0, y0, 0).color(r, gr, b, 0).endVertex();
            buf.vertex(m, x1, y1, 0).color(r, gr, b, a).endVertex();
            buf.vertex(m, x2, y2, 0).color(r, gr, b, a).endVertex();
        }
        tess.end();
        RenderSystem.disableBlend();
    }
}
