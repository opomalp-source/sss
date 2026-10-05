package com.dbzenith.client.ui;

import com.dbzenith.client.ClientStruggle;
import com.dbzenith.client.ModKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** The beam struggle tug-of-war: your colour from the left, theirs from the right, the clash where they meet. */
public final class StruggleOverlay implements IGuiOverlay {
    private float lastT = -1;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientStruggle.active() || mc.level == null || mc.options.hideGui) {
            lastT = -1;
            return;
        }
        float t = mc.level.getGameTime() + partial;
        float dt = lastT < 0 ? 1 : Mth.clamp(t - lastT, 0, 5);
        lastT = t;
        float balance = ClientStruggle.shownBalance(dt);                  // -1 losing .. 1 winning
        int w = 180, h = 12, x = width / 2 - w / 2, y = height - 66;          // above the hotbar, clear of the fighter
        float split = x + w * (0.5f + balance / 2);
        int mine = 0xFF000000 | ClientStruggle.mine(), theirs = 0xFF000000 | ClientStruggle.theirs();
        DbzTheme.slant(g, x - 2, y - 2, w + 4, h + 4, 6, 0xF0040508, 0xF0040508);
        DbzTheme.slant(g, x, y, split - x, h, 6, DbzTheme.brighten(mine, 1.2f), DbzTheme.darken(mine, 0.6f));
        DbzTheme.slant(g, split, y, x + w - split, h, 6, DbzTheme.brighten(theirs, 1.2f), DbzTheme.darken(theirs, 0.6f));
        float pulse = 0.6f + 0.4f * Mth.sin(t * 1.4f);
        DbzTheme.slant(g, split - 2, y - 3, 4, h + 6, 6, DbzTheme.withAlpha(0xFFFFFF, (int) (255 * pulse)), 0xC0FFFFFF);
        Component prompt = Component.translatable("hud.dbzenith.struggle", ModKeys.KI_ATTACK.getTranslatedKeyMessage());
        float pop = ClientStruggle.sinceMash() < 3 ? 1.25f : 1.1f;
        DbzTheme.text(g, mc.font, prompt, width / 2f - mc.font.width(prompt) * pop / 2, y - 14, balance < -0.4f ? 0xFFFF6A5A : DbzTheme.TITLE, pop);
    }
}
