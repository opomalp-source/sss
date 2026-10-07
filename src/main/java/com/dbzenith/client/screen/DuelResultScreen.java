package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.network.DuelResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A duel's results (CX-19 phase 9): VICTORY, DEFEAT or DRAW (or who won, for a watcher), how it ended, the rounds,
 * the rating change, and both sides' numbers side by side (damage, hits, longest combo, perfect guards, vanishes) with
 * bars to compare. Rematch challenges the same foe under the same rules.
 */
public class DuelResultScreen extends Screen {
    private static final int W = 300, H = 210;
    private static final String[] STATS = {"damage", "hits", "combo", "perfect_guards", "vanishes"};

    private final DuelResultPacket r;
    private int left, top;
    private float opened = -1;

    public DuelResultScreen(DuelResultPacket r) {
        super(Component.translatable("screen.dbzenith.duel_result"));
        this.r = r;
    }

    public static void open(DuelResultPacket r) {
        Minecraft.getInstance().setScreen(new DuelResultScreen(r));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (!r.watcher()) {
            addRenderableWidget(ThemedButton.of(Component.translatable("duel.dbzenith.rematch"), b -> {
                if (minecraft.player != null) minecraft.player.connection.sendCommand("duel " + r.foe() + " " + r.bestOf() + " " + r.rules());
                onClose();
            }).bounds(left + 10, top + H - 26, 120, 18).build());
        }
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - 90, top + H - 26, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (opened < 0) opened = minecraft.level == null ? 0 : minecraft.level.getGameTime() + partial;
        float t = minecraft.level == null ? 99 : minecraft.level.getGameTime() + partial - opened;
        renderBackground(g);
        DbzTheme.window(g, font, title, left, top, W, H);

        // the verdict
        Component verdict;
        int color;
        switch (r.outcome()) {
            case 1 -> { verdict = Component.translatable("duel.dbzenith.victory"); color = 0xFFFFD34A; }
            case 2 -> { verdict = Component.translatable("duel.dbzenith.defeat"); color = 0xFFFF6A5A; }
            case 3 -> { verdict = Component.translatable("duel.dbzenith.watched", r.myWins() > r.foeWins() ? r.me() : r.foe()); color = 0xFFFFD34A; }
            default -> { verdict = Component.translatable("duel.dbzenith.draw"); color = 0xFFAEE6FF; }
        }
        if (r.outcome() == 3 && r.myWins() == r.foeWins()) verdict = Component.translatable("duel.dbzenith.draw");
        float pop = 1 + 0.4f * Math.max(0, 1 - t / 6f);
        float vs = 2.2f * pop;
        DbzTheme.text(g, font, verdict, left + W / 2f - font.width(verdict) * vs / 2, top + 26, color, vs);
        Component how = Component.translatable("duel.dbzenith.reason." + r.reason());
        Component rounds = r.bestOf() > 1 ? Component.translatable("duel.dbzenith.rounds", r.myWins(), r.foeWins()) : Component.empty();
        Component sub = how.copy().append(r.bestOf() > 1 ? "  ·  " : "").append(rounds);
        DbzTheme.text(g, font, sub, left + W / 2f - font.width(sub) * 0.8f / 2, top + 50, DbzTheme.DIM, 0.8f);
        if (!r.watcher()) {
            String sign = r.ratingDelta() >= 0 ? "+" : "";
            Component rating = Component.translatable("duel.dbzenith.rating", r.rating(), sign + r.ratingDelta());
            DbzTheme.text(g, font, rating, left + W / 2f - font.width(rating) * 0.75f / 2, top + 61, r.ratingDelta() >= 0 ? DbzTheme.GOOD : DbzTheme.BAD, 0.75f);
        }

        // the numbers
        int y = top + 78, mid = left + W / 2;
        DbzTheme.text(g, font, r.me(), left + 14, y, DbzTheme.TITLE, 0.8f);
        DbzTheme.text(g, font, r.foe(), left + W - 14 - font.width(r.foe()) * 0.8f, y, DbzTheme.TITLE, 0.8f);
        y += 12;
        float grow = Mth.clamp(t / 12f, 0, 1);
        for (int i = 0; i < STATS.length; i++) {
            double a = r.myStats()[i], b = r.foeStats()[i], most = Math.max(1e-9, Math.max(a, b));
            Component label = Component.translatable("duel.dbzenith.stat." + STATS[i]);
            DbzTheme.text(g, font, label, mid - font.width(label) * 0.7f / 2, y, DbzTheme.TEXT, 0.7f);
            String av = i == 0 ? compact(a) : String.valueOf((int) a), bv = i == 0 ? compact(b) : String.valueOf((int) b);
            int barW = W / 2 - 60;
            int aw = (int) (barW * a / most * grow), bw = (int) (barW * b / most * grow);
            g.fill(mid - 30 - aw, y + 9, mid - 30, y + 12, a >= b ? 0xFFFFD34A : 0xFF8890A8);
            g.fill(mid + 30, y + 9, mid + 30 + bw, y + 12, b >= a ? 0xFFFFD34A : 0xFF8890A8);
            DbzTheme.text(g, font, av, left + 14, y + 2, a >= b ? DbzTheme.TITLE : DbzTheme.TEXT, 0.8f);
            DbzTheme.text(g, font, bv, left + W - 14 - font.width(bv) * 0.8f, y + 2, b >= a ? DbzTheme.TITLE : DbzTheme.TEXT, 0.8f);
            y += 17;
        }
        super.render(g, mouseX, mouseY, partial);
    }

    static String compact(double v) {
        if (v < 1000) return String.valueOf(Math.round(v));
        String[] units = {"K", "M", "B", "T"};
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v < 10 ? String.format("%.1f", v) : String.valueOf((int) v)) + units[u];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
