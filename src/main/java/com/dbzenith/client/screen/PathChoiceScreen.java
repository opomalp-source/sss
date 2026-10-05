package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PathPackets;
import com.dbzenith.race.Variant;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The first milestone's choice: one card per path of your race, with what it is about and the forms it leads to.
 * Choosing is permanent.
 */
public class PathChoiceScreen extends Screen {
    private static final int W = 404, H = 232;
    private final Screen parent;
    private int left, top;

    public PathChoiceScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.path_choice"));
        this.parent = parent;
    }

    private List<Variant> paths() {
        return Variant.paths(ClientPlayerData.get().getRace());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        List<Variant> paths = paths();
        int cw = paths.isEmpty() ? 0 : (W - 16 - (paths.size() - 1) * 6) / paths.size();
        for (int i = 0; i < paths.size(); i++) {
            Variant v = paths.get(i);
            addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.path_choose"), b -> {
                ModNetwork.sendToServer(new PathPackets.Choose(v.id()));
                onClose();
            }).bounds(left + 8 + i * (cw + 6) + 8, top + H - 48, cw - 16, 18).build());
        }
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.path_later"), b -> onClose())
                .bounds(left + W - 78, top + H - 24, 70, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.window(g, font, title, left, top, W, H);
        PlayerData d = ClientPlayerData.get();
        Component sub = Component.translatable("screen.dbzenith.path_sub");
        float ss = Math.min(1f, (W - 16f) / font.width(sub));
        DbzTheme.text(g, font, sub, left + W / 2f - font.width(sub) * ss / 2, top + 17, DbzTheme.DIM, ss);
        List<Variant> paths = paths();
        int cw = paths.isEmpty() ? 0 : (W - 16 - (paths.size() - 1) * 6) / paths.size();
        for (int i = 0; i < paths.size(); i++) {
            Variant v = paths.get(i);
            int x = left + 8 + i * (cw + 6), y = top + 30;
            boolean hot = mouseX >= x && mouseX < x + cw && mouseY >= y && mouseY < top + H - 28;
            int aura = 0xFF000000 | (v.auraColor() >= 0 ? v.auraColor() : 0xD8A040);
            DbzTheme.slant(g, x, y, cw, H - 60, 0, DbzTheme.withAlpha(DbzTheme.darken(aura, 0.35f), hot ? 210 : 160), 0xC0080A14);
            DbzTheme.slant(g, x, y, cw, 2, 0, aura, aura);
            Component name = Component.translatable(v.translationKey());
            float scale = Math.min(1.2f, (cw - 8f) / Math.max(1, font.width(name)));
            DbzTheme.text(g, font, name, x + cw / 2f - font.width(name) * scale / 2, y + 6, aura, scale);
            DbzTheme.wrapped(g, font, Component.translatable(v.descriptionKey()), x + 5, y + 22, cw - 10, DbzTheme.TEXT);
            // the forms this path leads to
            int fy = top + H - 92;
            DbzTheme.text(g, font, Component.translatable("screen.dbzenith.path_forms"), x + 5, fy, DbzTheme.DIM, 0.7f);
            int line = 0;
            for (Form f : Forms.forCharacter(d.getRace(), v)) {
                if (Forms.forCharacter(d.getRace(), Variant.defaultFor(d.getRace())).contains(f)) continue;   // only what is new
                if (line >= 3) break;
                int hair = f.hairColor();
                boolean dark = hair >= 0 && ((hair >> 16 & 255) + (hair >> 8 & 255) + (hair & 255)) < 150;   // black SSJ4 hair: use the aura
                DbzTheme.text(g, font, Component.translatable(f.translationKey()), x + 8, fy + 9 + line * 9,
                        0xFF000000 | (hair >= 0 && !dark ? hair : f.auraColor()), 0.75f);
                line++;
            }
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
