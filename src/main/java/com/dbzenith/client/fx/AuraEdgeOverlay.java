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
                boolean powering = state.has(PublicStatePacket.CHARGING) || state.has(PublicStatePacket.HEAVY);
                Form form = Forms.byId(state.form());
                boolean held = !form.isBase() || state.overdrive() > 0;
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
    }
}
