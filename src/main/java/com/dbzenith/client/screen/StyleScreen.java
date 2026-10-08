package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.style.StylePackets;
import com.dbzenith.style.StyleSlot;
import com.dbzenith.style.Styles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Styles page of the character menu (CX-20): every animation slot on its own row (stance, walk, sprint, hover,
 * flight, fast flight, charging, fighting stance, fighting steps), each set to the default or to any style you have
 * learned that fills it, with arrows to go through them. Your character stands on the right, moving as chosen; the
 * row picked shows its style's master and description. Styles are learned from masters (right-click one).
 */
public class StyleScreen extends MenuScreen {
    private static final int ROW = 22;
    private int picked;
    private String shown = "";

    public StyleScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.styles"), Page.STYLES, parent);
    }

    /** The choices for a slot: "" (the default), then each learned style that fills it. */
    static List<String> options(PlayerData d, StyleSlot slot) {
        List<String> out = new ArrayList<>();
        out.add("");
        for (Styles.Style s : Styles.all()) if (d.hasStyle(s.id()) && s.has(slot)) out.add(s.id());
        return out;
    }

    private String state() {
        PlayerData d = ClientPlayerData.get();
        return d.styleSlotsCode() + "|" + d.learnedStylesView() + "|" + Styles.version();
    }

    private void cycle(StyleSlot slot, int dir) {
        PlayerData d = ClientPlayerData.get();
        List<String> opts = options(d, slot);
        int i = Math.max(0, opts.indexOf(d.getStyleSlot(slot.id)));
        String next = opts.get(Math.floorMod(i + dir, opts.size()));
        ModNetwork.sendToServer(new StylePackets.Action(StylePackets.Action.Kind.EQUIP, slot.id, next, 0));
    }

    @Override
    protected void initPage() {
        shown = state();
        int x = cardX + 10, y = cardY + 26, w = (int) (cardW * 0.6f) - 20;
        PlayerData d = ClientPlayerData.get();
        for (int i = 0; i < StyleSlot.ALL.length; i++) {
            StyleSlot slot = StyleSlot.ALL[i];
            int ry = y + i * ROW, idx = i;
            boolean choice = options(d, slot).size() > 1;
            UiButton prev = UiButton.of(UiButton.Style.CHIP, Component.literal("◀"), x + w - 98, ry + 2, 14, 14, b -> {
                picked = idx;
                cycle(slot, -1);
            });
            UiButton next = UiButton.of(UiButton.Style.CHIP, Component.literal("▶"), x + w - 16, ry + 2, 14, 14, b -> {
                picked = idx;
                cycle(slot, 1);
            });
            prev.active = next.active = choice;
            addRenderableWidget(prev);
            addRenderableWidget(next);
            addRenderableWidget(UiButton.of(UiButton.Style.GHOST, Component.translatable(slot.nameKey()), x, ry + 2, w - 104, 14,
                    b -> picked = idx).textScale(0.8f));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!state().equals(shown)) rebuildWidgets();
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial) {
        PlayerData d = ClientPlayerData.get();
        int x = cardX + 10, y = cardY + 26, w = (int) (cardW * 0.6f) - 20;
        Ui.section(g, font, Component.translatable("screen.dbzenith.styles_slots"), x, cardY + 8, w);
        for (int i = 0; i < StyleSlot.ALL.length; i++) {
            StyleSlot slot = StyleSlot.ALL[i];
            int ry = y + i * ROW;
            if (i == picked) Ui.round(g, x - 4, ry, w + 8, ROW - 2, 4, 0x22FFB547);
            String id = d.getStyleSlot(slot.id);
            Styles.Style s = Styles.style(id);
            Component name = s == null ? Component.translatable("screen.dbzenith.styles_default") : Component.translatable(s.nameKey());
            int color = s == null ? Ui.MUTED : 0xFF000000 | s.color();
            float sc = Math.min(0.72f, 62f / Math.max(1, font.width(name)));                // long names shrink to fit between the arrows
            Ui.centered(g, font, name, x + w - 57, ry + 5.5f + (0.72f - sc) * 4, color, sc);
        }

        // the right: you, moving as chosen, and the picked row's style
        int rx = cardX + (int) (cardW * 0.6f) + 4, rw = cardX + cardW - rx - 10, cx = rx + rw / 2;
        Ui.platform(g, cx, cardY + 136, 40, 8, 0xFFB547);
        if (minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, cardY + 134, 48, cx - mouseX, cardY + 70 - mouseY, minecraft.player);
        }
        StyleSlot slot = StyleSlot.ALL[Math.max(0, Math.min(StyleSlot.ALL.length - 1, picked))];
        Styles.Style s = Styles.style(d.getStyleSlot(slot.id));
        int ty = cardY + 146;
        Ui.text(g, font, Component.translatable(slot.nameKey()).getString().toUpperCase(), rx, ty, Ui.GOLD, 0.7f);
        ty += 9;
        if (s == null) {
            ty += Ui.paragraph(g, font, Component.translatable("screen.dbzenith.styles_default_desc"), rx, ty, rw, Ui.MUTED, 0.62f, 4);
        } else {
            Ui.text(g, font, Component.translatable(s.nameKey()), rx, ty, 0xFF000000 | s.color(), 0.8f);
            ty += 9;
            Ui.text(g, font, Component.translatable("screen.dbzenith.styles_from", Component.translatable("entity.dbzenith." + s.master())), rx, ty, Ui.FAINT, 0.62f);
            ty += 8;
            ty += Ui.paragraph(g, font, Component.translatable(s.descKey()), rx, ty, rw, Ui.MUTED, 0.62f, 3);
        }
        int learned = d.learnedStylesView().size(), all = Styles.all().size();
        Ui.text(g, font, Component.translatable("screen.dbzenith.styles_count", learned, all), rx, cardY + cardH - 10, Ui.FAINT, 0.6f);
    }
}
