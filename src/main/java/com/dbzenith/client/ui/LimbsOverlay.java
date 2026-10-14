package com.dbzenith.client.ui;

import com.dbzenith.combat.Limbs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Your limbs on the HUD (CX-53): a small figure at the left edge, shown only while a limb is hurt. Each limb is coloured
 * by its health (green, amber, red when broken), a lost limb is an empty outline, a prosthetic steel blue.
 */
public final class LimbsOverlay implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Limbs.State s = Limbs.of(mc.player);
        if (s.whole()) return;
        int x = 8, y = height / 2 - 30;
        g.fill(x - 3, y - 3, x + 25, y + 59, 0x60000000);
        g.fill(x + 7, y, x + 15, y + 8, 0xFFB0B0B0);                       // head
        g.fill(x + 6, y + 9, x + 16, y + 28, 0xFFB0B0B0);                  // body
        limb(g, s, Limbs.Limb.RIGHT_ARM, x, y + 9, 5, 18);                  // your right is on the figure's left as you look at it
        limb(g, s, Limbs.Limb.LEFT_ARM, x + 17, y + 9, 5, 18);
        limb(g, s, Limbs.Limb.RIGHT_LEG, x + 6, y + 29, 4, 24);
        limb(g, s, Limbs.Limb.LEFT_LEG, x + 12, y + 29, 4, 24);
    }

    private static void limb(GuiGraphics g, Limbs.State s, Limbs.Limb l, int x, int y, int w, int h) {
        if (s.severed(l) && !s.prosthetic(l)) {                            // gone: just where it was
            int c = 0xA0FF3030;
            g.fill(x, y, x + w, y + 1, c);
            g.fill(x, y + h - 1, x + w, y + h, c);
            g.fill(x, y, x + 1, y + h, c);
            g.fill(x + w - 1, y, x + w, y + h, c);
            return;
        }
        int hp = s.health(l);
        int c = s.prosthetic(l) ? 0xFF7890B0 : hp >= 75 ? 0xFF50D060 : hp >= Limbs.USABLE ? 0xFFE0A030 : 0xFFE03030;
        g.fill(x, y, x + w, y + h, c);
        if (!s.prosthetic(l) && hp < 100) g.fill(x, y, x + w, y + h - Math.round(h * hp / 100f), 0x50000000);   // how much is left, from the top
    }
}
