package com.dbzenith.client.screen;

import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.TournamentPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The World Martial Arts Tournament's board (CX-17c), from the Announcer: who has signed up and how long registration
 * stays open, then the bracket of eight, its lines filling in gold as fighters go through, the match being fought
 * pulsing. Enter from here.
 */
public class TournamentScreen extends Screen {
    /** The last state the server sent; the screen follows it. */
    public static TournamentPackets.State latest;

    private static final int BOX_W = 440, BOX_H = 250, SLOT_W = 92, SLOT_H = 16;
    private int left, top, age;
    private TournamentPackets.State shown;

    public TournamentScreen() {
        super(Component.translatable("screen.dbzenith.tournament"));
    }

    @Override
    protected void init() {
        left = (width - BOX_W) / 2;
        top = (height - BOX_H) / 2;
        shown = latest;
        String why = latest == null ? "" : latest.problem();
        UiButton enter = UiButton.of(UiButton.Style.PRIMARY, Component.translatable("screen.dbzenith.tournament.enter"),
                left + BOX_W - 148, top + BOX_H - 26, 140, 18, b -> ModNetwork.sendToServer(new TournamentPackets.Join()));
        enter.active = why.isEmpty();
        if (!why.isEmpty()) enter.tip(Component.translatable(why));
        addRenderableWidget(enter);
        addRenderableWidget(UiButton.of(UiButton.Style.GHOST, Component.translatable("gui.done"), left + 8, top + BOX_H - 26, 60, 18, b -> onClose()));
    }

    @Override
    public void tick() {
        age++;
        if (latest != shown) rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component name(TournamentPackets.Name n) {
        return n.player() ? Component.literal(n.text()) : Component.translatable("entity.dbzenith." + n.text());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.backdrop(g, width, height, 0.5f, 0.25f, 0xFFB547);
        Ui.card(g, left, top, BOX_W, BOX_H);
        Ui.text(g, font, title, left + 12, top + 10, Ui.GOLD, 1.25f);
        TournamentPackets.State s = latest;
        Component sub;
        if (s == null || s.phase() < 0) sub = Component.translatable("screen.dbzenith.tournament.none");
        else if (!s.drawn()) sub = Component.translatable("screen.dbzenith.tournament.signup", s.seconds());
        else if (s.match() >= 7) sub = Component.translatable("screen.dbzenith.tournament.over");
        else sub = Component.translatable("screen.dbzenith.tournament.round", Component.translatable("tournament.dbzenith.round." + (s.match() < 4 ? 0 : s.match() < 6 ? 1 : 2)));
        Ui.text(g, font, sub, left + 12, top + 26, Ui.MUTED, 0.85f);
        if (s != null && s.phase() >= 0 && s.drawn() && s.names().size() == 8) bracket(g, s);
        else if (s != null && s.phase() >= 0) {
            Ui.section(g, font, Component.translatable("screen.dbzenith.tournament.entrants"), left + 12, top + 44, BOX_W - 24);
            for (int i = 0; i < s.names().size(); i++) slot(g, left + 12 + (i % 2) * (SLOT_W + 12), top + 60 + (i / 2) * (SLOT_H + 6), name(s.names().get(i)), 0, true);
            Ui.paragraph(g, font, Component.translatable("screen.dbzenith.tournament.rules"), left + 230, top + 60, BOX_W - 242, Ui.MUTED, 0.75f, 12);
        } else {
            Ui.paragraph(g, font, Component.translatable("screen.dbzenith.tournament.rules"), left + 12, top + 50, BOX_W - 24, Ui.MUTED, 0.85f, 12);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** The bracket: quarterfinal slots on the left, then semifinals, the final, the champion. */
    private void bracket(GuiGraphics g, TournamentPackets.State s) {
        int x0 = left + 12, y0 = top + 46, gapX = 104, rowH = 22;
        int[][] ys = new int[4][];
        ys[0] = new int[8];
        for (int i = 0; i < 8; i++) ys[0][i] = y0 + i * rowH;
        for (int r = 1; r < 4; r++) {
            ys[r] = new int[ys[r - 1].length / 2];
            for (int i = 0; i < ys[r].length; i++) ys[r][i] = (ys[r - 1][2 * i] + ys[r - 1][2 * i + 1]) / 2;
        }
        int[] firstMatch = {0, 4, 6};
        for (int r = 0; r < 3; r++) {                                             // lines into the next column
            for (int i = 0; i < ys[r].length; i++) {
                int m = firstMatch[r] + i / 2;
                int slotHere = slotAt(s, r, i);
                boolean through = slotHere >= 0 && s.winners()[m] == slotHere;
                int x = x0 + r * gapX + SLOT_W, xm = x0 + (r + 1) * gapX - 6, y = ys[r][i] + SLOT_H / 2;
                int c = through ? Ui.GOLD : Ui.LINE;
                g.fill(x, y, xm, y + 1, c);
                g.fill(xm, Math.min(y, ys[r + 1][i / 2] + SLOT_H / 2), xm + 1, Math.max(y, ys[r + 1][i / 2] + SLOT_H / 2) + 1, c);
                g.fill(xm, ys[r + 1][i / 2] + SLOT_H / 2, x0 + (r + 1) * gapX, ys[r + 1][i / 2] + SLOT_H / 2 + 1, c);
            }
        }
        for (int r = 0; r < 4; r++) {
            for (int i = 0; i < ys[r].length; i++) {
                int slot = slotAt(s, r, i);
                Component n = slot < 0 ? Component.literal("?") : name(s.names().get(slot));
                int m = r < 3 ? firstMatch[r] + i / 2 : -1;
                int state = 0;                                                     // 0 waiting, 1 through, 2 out, 3 fighting now
                if (slot >= 0 && m >= 0 && s.winners()[m] >= 0) state = s.winners()[m] == slot ? 1 : 2;
                if (slot >= 0 && m >= 0 && m == s.match() && s.winners()[m] < 0) state = 3;
                if (r == 3 && slot >= 0) state = 1;
                slot(g, x0 + r * gapX, ys[r][i], n, state, slot >= 0);
            }
        }
        Ui.text(g, font, Component.translatable("screen.dbzenith.tournament.champion"), x0 + 3 * gapX, ys[3][0] - 10, Ui.GOLD, 0.7f);
    }

    /** Which slot reached column {@code r}, place {@code i} (-1 if not decided yet). */
    private static int slotAt(TournamentPackets.State s, int r, int i) {
        if (r == 0) return i;
        int m = (r == 1 ? 0 : r == 2 ? 4 : 6) + i;
        return s.winners()[m];
    }

    private void slot(GuiGraphics g, int x, int y, Component name, int state, boolean known) {
        int bg = state == 1 ? 0x40FFB547 : state == 3 ? (0x30 + (int) (Math.sin(age * 0.3) * 0x18 + 0x18) << 24 | 0xFF6040) : 0x18FFFFFF;
        Ui.round(g, x, y, SLOT_W, SLOT_H, 3, bg);
        if (state == 1) Ui.outline(g, x, y, SLOT_W, SLOT_H, 3, Ui.GOLD);
        int color = !known ? Ui.FAINT : state == 2 ? Ui.FAINT : state == 1 ? Ui.GOLD : Ui.TEXT;
        String text = font.substrByWidth(name, (int) ((SLOT_W - 8) / 0.8f)).getString();
        Ui.text(g, font, text, x + 5, y + 5, color, 0.8f);
    }
}
