package com.dbzenith.client.ui;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ClientPvp;
import com.dbzenith.combat.meter.Meters;
import com.dbzenith.data.PlayerData;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * The PvP meters (CX-20), bottom right and only in PvP mode: two upright bars in dark metal frames.
 * <ul>
 *   <li><b>Right, the form meter</b>, filled in your aura colour; a stud for each form you have unlocked.</li>
 *   <li><b>Left, the technique meter</b>, filled in the technique's colour (Kaioken red, the Ultra Instinct Sign grey,
 *       Mastered Ultra Instinct white); a stud for each Kaioken stage you know and for Ultra Instinct.</li>
 * </ul>
 * A stud the fill has reached lights up with a soft glow in the bar's colour. The fill is the texture cut at the value
 * (never stretched), easing to new values. Textures in textures/gui/hud/ at four times their size on screen.
 */
public final class MeterBars implements IGuiOverlay {
    /** GUI pixels: one bar, the opening inside its frame, the gap between the bars, and the margin from the corner. */
    public static final int W = 11, H = 112, IX = 2, IY = 4, IW = 7, IH = 104, GAP = 3, MARGIN = 6;
    public static final int TEX = 4;
    /** How fast the shown fill follows the real value (share of the gap closed per tick). */
    public static final float EASE = 0.25f;

    private static final ResourceLocation FRAME = tex("meter_frame"), BACK = tex("meter_back"), FORM_FILL = tex("form_meter_fill"),
            TECH_FILL = tex("tech_meter_fill"), STUD = tex("meter_stud"), STUD_LIT = tex("meter_stud_lit"), GLOW = tex("meter_stud_glow");

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(DBZenith.MOD_ID, "textures/gui/hud/" + name + ".png");
    }

    private static float shownForm = 0, shownTech = 0;
    private static float lastT = -1;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientPvp.on() || com.dbzenith.client.ClientFusion.cinematic()) {
            lastT = -1;
            return;
        }
        PlayerData d = ClientPlayerData.get();
        float t = mc.player.tickCount + partial;
        float dt = lastT < 0 ? 100 : Math.max(0, t - lastT);
        lastT = t;
        float k = 1 - (float) Math.pow(1 - EASE, dt);
        shownForm += ((float) d.getFormMeter() - shownForm) * k;
        shownTech += ((float) d.getTechMeter() - shownTech) * k;

        var pub = com.dbzenith.client.ClientPublicStates.get(mc.player.getId());
        int aura = 0xFFFFFF & (pub != null ? pub.auraColor() : com.dbzenith.ki.Aura.DEFAULT_COLOR);
        List<Meters.Step> forms = Meters.formSteps(d), techs = Meters.techSteps(d);
        int right = width - MARGIN - W, left = right - GAP - W, top = height - MARGIN - H;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        bar(g, left, top, TECH_FILL, shownTech, d.getTechMeter(), techs, Meters.techColor(techs, d.getTechMeter()), t);
        bar(g, right, top, FORM_FILL, shownForm, d.getFormMeter(), forms, aura, t);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** One bar: the backing, the fill tinted and cut at the value, the frame, the studs, and the glow of reached ones. */
    private static void bar(GuiGraphics g, int x, int y, ResourceLocation fill, float shown, double value, List<Meters.Step> steps, int rgb, float t) {
        float r = (rgb >> 16 & 255) / 255f, gr = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        blit(g, BACK, x + IX, y + IY, IW, IH, 0, IW * TEX, IH * TEX);
        float f = Mth.clamp(shown / 100f, 0, 1);
        int fillTex = Math.round(f * IH * TEX);                                        // in texture pixels, so it moves in quarter steps
        if (fillTex > 0) {
            Meters.Step top = Meters.reached(steps, value);
            float pulse = top != null && value >= 100 - 1e-6 ? 0.88f + 0.12f * Mth.sin(t * 0.3f) : 1f;   // full: a slow breath
            RenderSystem.setShaderColor(r * pulse, gr * pulse, b * pulse, 1);
            blit(g, fill, x + IX, y + IY + IH - fillTex / (float) TEX, IW, fillTex / (float) TEX, IH * TEX - fillTex, IW * TEX, fillTex);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            float edge = y + IY + IH - fillTex / (float) TEX;                          // a bright rim on the fill's top
            int rim = 0xE0000000 | DbzTheme.mix(0xFF000000 | rgb, 0xFFFFFFFF, 0.55f) & 0xFFFFFF;
            g.pose().pushPose();
            g.pose().translate(0, edge, 0);
            g.pose().scale(1, 0.5f, 1);
            g.fill(x + IX, 0, x + IX + IW, 1, rim);
            g.flush();                                                                 // before the frame and studs go over it
            g.pose().popPose();
        }
        blit(g, FRAME, x, y, W, H, 0, W * TEX, H * TEX);
        for (Meters.Step s : steps) {
            float sy = y + IY + IH * (1 - (float) (s.at() / 100.0));
            boolean lit = value + 1e-6 >= s.at();
            if (lit) {                                                                 // the glow, added in the bar's colour
                float gl = 0.45f + 0.1f * Mth.sin(t * 0.18f + (float) s.at());       // subtle, breathing slowly
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                RenderSystem.setShaderColor(r, gr, b, gl);
                blit(g, GLOW, x + W / 2f - 8, sy - 4, 16, 8, 0, 64, 32);
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            blit(g, lit ? STUD_LIT : STUD, x - 1, sy - 1.5f, W + 2, 3, 0, (W + 2) * TEX, 3 * TEX);
        }
    }

    /**
     * Part of a texture drawn at a quarter of its size: {@code w x h} GUI pixels at x, y, from texture row {@code v},
     * {@code tw x th} texture pixels.
     */
    private static void blit(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, int v, int tw, int th) {
        if (th <= 0) return;
        RenderSystem.enableBlend();                                                    // a flush of the GUI batch turns it off
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1f / TEX, 1f / TEX, 1);
        int full = tex == BACK || tex == FORM_FILL || tex == TECH_FILL ? IH * TEX : th + v;
        g.blit(tex, 0, 0, tw, th, 0, v, tw, th, tw, full);
        g.pose().popPose();
    }
}
