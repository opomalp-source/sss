package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.TechniquePackets;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueLibrary;
import com.dbzenith.skill.Techniques;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Techniques page of the character menu (UI v3): every technique your race can use on the left (click to learn
 * with TP, or to equip / unequip one you know), your deck as numbered slots on the right (click a slot to empty it),
 * and the hovered technique's details along the bottom. Requests go to the server; the page redraws from synced data.
 */
public class DeckScreen extends MenuScreen {
    private static final int ROW = 15;
    /** First library row shown (the list scrolls with the mouse wheel). */
    private int scroll;

    public DeckScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.techniques"), Page.TECHNIQUES, parent);
    }

    private int listX() { return cardX + 8; }
    private int listW() { return (int) (cardW * 0.6f) - 12; }
    private int listY() { return cardY + 20; }
    private int visible() { return Math.max(3, (cardH - 20 - 46) / ROW); }
    private int deckX() { return cardX + (int) (cardW * 0.6f) + 2; }
    private int deckW() { return cardX + cardW - 8 - deckX(); }

    @Override
    protected void initPage() {
        addRenderableWidget(UiButton.of(UiButton.Style.SECONDARY, Component.translatable("screen.dbzenith.ki_creator"),
                deckX(), cardY + cardH - 22, deckW(), 15, b -> minecraft.setScreen(new KiCreatorScreen(this))).textScale(0.8f));
    }

    /** Your Ki Creator techniques first, then everything your race can learn. */
    private List<Technique> library() {
        PlayerData d = ClientPlayerData.get();
        List<Technique> out = new ArrayList<>();
        for (int i = 0; i < com.dbzenith.skill.CustomTechniques.MAX_SLOTS; i++) {
            Technique t = d.customTechnique(com.dbzenith.skill.CustomTechniques.id(i));
            if (t != null) out.add(t);
        }
        out.addAll(Techniques.forRace(d.getRace()));
        return out;
    }

    private int listRow(double mx, double my) {
        if (mx < listX() || mx > listX() + listW() || my < listY()) return -1;
        int i = (int) ((my - listY()) / ROW);
        if (i >= visible()) return -1;
        i += scroll;
        return i < library().size() ? i : -1;
    }

    private int deckRow(double mx, double my) {
        int y0 = cardY + 20;
        if (mx < deckX() || mx > deckX() + deckW() || my < y0) return -1;
        int i = (int) ((my - y0) / 17);
        return i < ClientPlayerData.get().deckView().size() ? i : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, library().size() - visible()), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerData d = ClientPlayerData.get();
        int li = listRow(mouseX, mouseY);
        if (li >= 0) {
            Technique t = library().get(li);
            if (d.knows(t.id())) {
                List<String> deck = new ArrayList<>(d.deckView());
                if (!deck.remove(t.id())) deck.add(t.id());
                ModNetwork.sendToServer(new TechniquePackets.SetDeck(deck));
            } else {
                ModNetwork.sendToServer(new TechniquePackets.Learn(t.id()));
            }
            com.dbzenith.client.ClientSounds.uiClick();
            return true;
        }
        int di = deckRow(mouseX, mouseY);
        if (di >= 0) {
            List<String> deck = new ArrayList<>(d.deckView());
            deck.remove(di);
            ModNetwork.sendToServer(new TechniquePackets.SetDeck(deck));
            com.dbzenith.client.ClientSounds.uiClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial) {
        PlayerData d = ClientPlayerData.get();
        List<Technique> lib = library();
        int lx = listX(), lw = listW(), vis = visible();
        Ui.section(g, font, Component.translatable("screen.dbzenith.library"), lx, cardY + 8, lw);
        int hover = listRow(mouseX, mouseY);
        scroll = Math.max(0, Math.min(Math.max(0, lib.size() - vis), scroll));
        for (int i = scroll; i < Math.min(lib.size(), scroll + vis); i++) {
            Technique t = lib.get(i);
            int y = listY() + (i - scroll) * ROW;
            boolean equipped = d.deckView().contains(t.id()), known = d.knows(t.id());
            Ui.round(g, lx, y, lw, ROW - 2, 2, i == hover ? 0x22FFFFFF : equipped ? 0x14FFB547 : 0x0AFFFFFF);
            if (equipped) g.fill(lx, y + 2, lx + 2, y + ROW - 4, Ui.GOLD);
            Ui.round(g, lx + 6, y + 4, 5, 5, 1, 0xFF000000 | t.color());                    // the technique's colour
            Ui.text(g, font, t.name(), lx + 15, y + 3, known ? Ui.TEXT : 0xFFB8BECC, 0.8f);
            if (t.isCustom()) Ui.text(g, font, Component.literal("✦"), lx + 15 + font.width(t.name()) * 0.8f + 3, y + 3, Ui.GOLD, 0.7f);
            Component status;
            int sc;
            if (equipped) { status = Component.translatable("screen.dbzenith.equipped"); sc = 0xFF8CE08C; }
            else if (known) { status = Component.translatable("screen.dbzenith.learned"); sc = Ui.MUTED; }
            else if (problemIsLevel(d, t)) { status = Component.translatable("screen.dbzenith.needs_level", t.unlockLevel()); sc = 0xFFE07068; }
            else { status = Component.translatable("screen.dbzenith.learn_for", t.learnCost()); sc = Ui.GOLD; }
            Ui.text(g, font, status, lx + lw - 6 - font.width(status) * 0.7f, y + 3.5f, sc, 0.7f);
        }
        if (lib.size() > vis) {                                                           // the scroll thumb
            int trackH = vis * ROW - 2, barH = Math.max(10, trackH * vis / lib.size());
            int barY = listY() + (trackH - barH) * scroll / Math.max(1, lib.size() - vis);
            g.fill(lx + lw + 3, listY(), lx + lw + 4, listY() + trackH, 0x20FFFFFF);
            Ui.round(g, lx + lw + 2, barY, 3, barH, 1, 0xA0FFFFFF);
        }

        // the deck
        int dx = deckX(), dw = deckW();
        Ui.section(g, font, Component.translatable("screen.dbzenith.deck", d.deckView().size(), slots(d)), dx, cardY + 8, dw);
        int dh = deckRow(mouseX, mouseY);
        for (int i = 0; i < slots(d); i++) {
            int y = cardY + 20 + i * 17;
            if (y + 15 > cardY + cardH - 26) break;
            Technique t = i < d.deckView().size() ? Techniques.resolve(d, d.deckView().get(i)) : null;
            if (t == null) {                                                               // an empty slot
                Ui.outline(g, dx, y, dw, 15, 2, 0x22FFFFFF);
                Ui.text(g, font, String.valueOf(i + 1), dx + 5, y + 4, Ui.FAINT, 0.7f);
                continue;
            }
            Ui.round(g, dx, y, dw, 15, 2, i == dh ? 0x30E06060 : 0x16FFFFFF);
            Ui.outline(g, dx, y, dw, 15, 2, i == dh ? 0x80E06060 : 0x26FFFFFF);
            Ui.text(g, font, String.valueOf(i + 1), dx + 5, y + 4, Ui.GOLD, 0.7f);
            Ui.round(g, dx + 14, y + 5, 5, 5, 1, 0xFF000000 | t.color());
            Ui.text(g, font, t.name(), dx + 23, y + 4, Ui.TEXT, 0.75f);
            if (i == dh) Ui.text(g, font, Component.literal("×"), dx + dw - 9, y + 3, 0xFFE07068, 0.85f);
        }

        // details of the hovered technique, or the help
        int by = cardY + cardH - 26;
        g.fill(lx, by - 4, lx + lw, by - 3, Ui.LINE_SOFT);
        if (hover >= 0) {
            Technique t = lib.get(hover);
            Component info = Component.translatable("screen.dbzenith.technique_info", (int) t.kiCost(),
                    String.format("%.1f", t.cooldownTicks() / 20.0), String.format("%.1f", t.damageMult()),
                    (int) com.dbzenith.skill.TechniqueMastery.get(d, t), t.description());
            Ui.paragraph(g, font, info, lx, by, lw, 0xFFC8CEDC, 0.7f, 3);
        } else {
            Ui.paragraph(g, font, Component.translatable("screen.dbzenith.techniques_help"), lx, by, lw, Ui.MUTED, 0.7f, 3);
        }
    }

    private static boolean problemIsLevel(PlayerData d, Technique t) {
        try {
            return com.dbzenith.stats.StatCalculator.level(d) < t.unlockLevel();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static int slots(PlayerData d) {
        try {
            return TechniqueLibrary.deckSlots(d);
        } catch (IllegalStateException e) {
            return 4;
        }
    }
}
