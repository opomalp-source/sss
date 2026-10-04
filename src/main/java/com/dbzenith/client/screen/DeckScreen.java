package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.TechniquePackets;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueLibrary;
import com.dbzenith.skill.Techniques;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Techniques screen: every technique your race can use (learn with TP, click to equip/unequip) and the deck
 * that R/Y use. Requests go to the server; the screen redraws from synced data.
 */
public class DeckScreen extends Screen {
    private static final int W = 380;
    private static final int H = 232;
    private static final int ROW = 15;
    private static final int PANEL = 0xE0101018;
    private static final int HEADER = 0xFFFFB330;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int DIM = 0xFFA0A0B0;
    private static final int GOOD = 0xFF7CFF7C;
    private static final int GOLD = 0xFFFFD040;
    private static final int BAD = 0xFFFF7070;

    private final Screen parent;
    private int left;
    private int top;
    /** First library row shown (the list scrolls with the mouse wheel). */
    private int scroll;
    private static final int VISIBLE = 11;

    public DeckScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.techniques"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + W - 70, top + H - 24, 62, 18).build());
    }

    private List<Technique> library() {
        return Techniques.forRace(ClientPlayerData.get().getRace());
    }

    private int listRow(double mx, double my) {
        int y0 = top + 34;
        if (mx < left + 6 || mx > left + 236 || my < y0) return -1;
        int i = (int) ((my - y0) / ROW);
        if (i >= VISIBLE) return -1;
        i += scroll;
        return i < library().size() ? i : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, library().size() - VISIBLE), scroll - (int) Math.signum(delta)));
        return true;
    }

    private int deckRow(double mx, double my) {
        int y0 = top + 46;
        if (mx < left + 246 || mx > left + W - 6 || my < y0) return -1;
        int i = (int) ((my - y0) / ROW);
        return i < ClientPlayerData.get().deckView().size() ? i : -1;
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
            return true;
        }
        int di = deckRow(mouseX, mouseY);
        if (di >= 0) {
            List<String> deck = new ArrayList<>(d.deckView());
            deck.remove(di);
            ModNetwork.sendToServer(new TechniquePackets.SetDeck(deck));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, PANEL);
        PlayerData d = ClientPlayerData.get();
        g.drawString(font, title, left + 8, top + 8, HEADER);
        g.drawString(font, Component.translatable("screen.dbzenith.tp", String.format("%,d", d.getTrainingPoints())), left + 8, top + 20, GOOD);
        g.drawString(font, Component.translatable("screen.dbzenith.techniques_help"), left + 110, top + 20, DIM);

        List<Technique> lib = library();
        int hover = listRow(mouseX, mouseY);
        Technique tooltipFor = null;
        scroll = Math.max(0, Math.min(Math.max(0, lib.size() - VISIBLE), scroll));
        for (int i = scroll; i < Math.min(lib.size(), scroll + VISIBLE); i++) {
            Technique t = lib.get(i);
            int y = top + 34 + (i - scroll) * ROW;
            if (i == hover) {
                g.fill(left + 4, y - 3, left + 238, y + ROW - 4, 0x30FFFFFF);
                tooltipFor = t;
            }
            g.drawString(font, Component.translatable(t.translationKey()), left + 10, y, 0xFF000000 | t.color());
            int sx = left + 140;
            if (d.deckView().contains(t.id())) g.drawString(font, Component.translatable("screen.dbzenith.equipped"), sx, y, GOOD);
            else if (d.knows(t.id())) g.drawString(font, Component.translatable("screen.dbzenith.learned"), sx, y, TEXT);
            else if (problemIsLevel(d, t)) g.drawString(font, Component.translatable("screen.dbzenith.needs_level", t.unlockLevel()), sx, y, BAD);
            else g.drawString(font, Component.translatable("screen.dbzenith.learn_for", t.learnCost()), sx, y, GOLD);
        }
        if (lib.size() > VISIBLE) { // scroll bar
            int trackTop = top + 32, trackH = VISIBLE * ROW;
            int barH = Math.max(10, trackH * VISIBLE / lib.size());
            int barY = trackTop + (trackH - barH) * scroll / Math.max(1, lib.size() - VISIBLE);
            g.fill(left + 239, trackTop, left + 241, trackTop + trackH, 0x40FFFFFF);
            g.fill(left + 239, barY, left + 241, barY + barH, 0xC0FFFFFF);
        }

        int dx = left + 248;
        g.fill(dx - 4, top + 32, left + W - 4, top + H - 30, 0x40000000);
        g.drawString(font, Component.translatable("screen.dbzenith.deck", d.deckView().size(), slots(d)), dx, top + 34, HEADER);
        int dh = deckRow(mouseX, mouseY);
        for (int i = 0; i < d.deckView().size(); i++) {
            Technique t = Techniques.byId(d.deckView().get(i));
            if (t == null) continue;
            int y = top + 46 + i * ROW;
            if (i == dh) g.fill(dx - 2, y - 3, left + W - 6, y + ROW - 4, 0x30FF6060);
            g.drawString(font, (i + 1) + ". ", dx, y, DIM);
            g.drawString(font, Component.translatable(t.translationKey()), dx + 14, y, 0xFF000000 | t.color());
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (tooltipFor != null) {
            Technique t = tooltipFor;
            g.renderTooltip(font, font.split(Component.translatable("screen.dbzenith.technique_info", (int) t.kiCost(),
                    String.format("%.1f", t.cooldownTicks() / 20.0), String.format("%.1f", t.damageMult()),
                    (int) com.dbzenith.skill.TechniqueMastery.get(d, t),
                    Component.translatable(t.translationKey() + ".desc")), 200), mouseX, mouseY);
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

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
