package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.RacialPackets;
import com.dbzenith.race.RacialSkill;
import com.dbzenith.race.RacialSkills;
import com.dbzenith.race.Variant;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Your race's skills: every passive and active for your race and lineage, what unlocks them, what they do. Click an
 * unlocked active to put it on the Racial key.
 */
public class RacialScreen extends Screen {
    private static final int W = 420, H = 248, CARD_W = 196, CARD_H = 30, COLS = 2, ROWS = 4;
    private final Screen parent;
    private int left, top, scroll;
    private RacialSkill focused;

    public RacialScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.racial"));
        this.parent = parent;
    }

    private List<RacialSkill> skills() {
        PlayerData d = ClientPlayerData.get();
        return RacialSkills.forCharacter(d.getRace(), d.getVariant());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + W - 64, top + H - 22, 56, 16).build());
    }

    private int maxScroll() {
        int rows = (skills().size() + COLS - 1) / COLS;
        return Math.max(0, rows - ROWS);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    private RacialSkill at(double mx, double my) {
        List<RacialSkill> all = skills();
        for (int i = 0; i < all.size(); i++) {
            int row = i / COLS - scroll, col = i % COLS;
            if (row < 0 || row >= ROWS) continue;
            int x = left + 10 + col * (CARD_W + 8), y = top + 34 + row * (CARD_H + 4);
            if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H) return all.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        RacialSkill s = at(mx, my);
        if (s != null) {
            focused = s;
            PlayerData d = ClientPlayerData.get();
            if (s.isActive() && RacialSkills.unlocked(d, s)) {
                ModNetwork.sendToServer(new RacialPackets.Select(s.id()));
                d.setRacialSelected(s.id());                       // at once; the server's sync confirms it
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        DbzTheme.screenBackground(g, width, height);
        PlayerData d = ClientPlayerData.get();
        Variant v = d.getVariant();
        Component who = Component.translatable(v.kind() == Variant.Kind.DEFAULT ? d.getRace().translationKey() : v.translationKey());
        DbzTheme.window(g, font, Component.translatable("screen.dbzenith.racial_title", who), left, top, W, H);
        int level = StatCalculator.level(d);
        Component sub = Component.translatable("screen.dbzenith.racial_sub", level);
        DbzTheme.text(g, font, sub, left + W / 2f - font.width(sub) * 0.75f / 2, top + 20, DbzTheme.DIM, 0.75f);

        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        RacialSkill hover = at(mouseX, mouseY);
        List<RacialSkill> all = skills();
        for (int i = 0; i < all.size(); i++) {
            int row = i / COLS - scroll, col = i % COLS;
            if (row < 0 || row >= ROWS) continue;
            RacialSkill s = all.get(i);
            int x = left + 10 + col * (CARD_W + 8), y = top + 34 + row * (CARD_H + 4);
            boolean open = RacialSkills.unlocked(d, s), selected = s.id().equals(d.getRacialSelected()), hot = s == hover || s == focused;
            int c = s.color();
            int fill = open ? DbzTheme.withAlpha(DbzTheme.darken(c, 0.25f), hot ? 200 : 140) : 0xA0181820;
            DbzTheme.slant(g, x, y, CARD_W, CARD_H, 4, fill, 0xC0080A12);
            if (selected) {
                DbzTheme.slant(g, x - 1, y - 1, CARD_W + 2, 2, 0, c, c);
                DbzTheme.slant(g, x - 1, y + CARD_H - 1, CARD_W + 2, 2, 0, c, c);
            }
            // emblem: a diamond in the skill's colour, ringed for actives
            int ex = x + 15, ey = y + CARD_H / 2;
            int ec = open ? c : 0xFF505058;
            DbzTheme.quad(g, ex, ey - 9, ex + 9, ey, ex, ey + 9, ex - 9, ey, 0xFF07070C, 0xFF07070C, 0xFF07070C, 0xFF07070C);
            DbzTheme.quad(g, ex, ey - 7, ex + 7, ey, ex, ey + 7, ex - 7, ey, DbzTheme.brighten(ec, 1.3f), ec, DbzTheme.darken(ec, 0.6f), ec);
            if (s.isActive()) DbzTheme.arc(g, ex, ey, 9.5f, 11f, 0, 360, ec, ec);
            if (s.isActive() && open && now < d.getRacialCooldown(s.id())) {
                float rest = (d.getRacialCooldown(s.id()) - now) / (float) Math.max(1, s.cooldownTicks());
                DbzTheme.arc(g, ex, ey, 0, 8, -90, 360 * rest, 0xB0000000, 0xB0000000);
            }
            Component name = Component.translatable(s.translationKey());
            DbzTheme.text(g, font, name, x + 30, y + 5, open ? DbzTheme.TEXT : DbzTheme.DIM, 0.9f);
            Component tag = Component.translatable(s.isActive() ? "screen.dbzenith.racial_active" : "screen.dbzenith.racial_passive");
            DbzTheme.text(g, font, tag, x + 30, y + 17, open ? DbzTheme.brighten(c, 1.15f) : 0xFF606068, 0.65f);
            Component state = open ? (selected ? Component.translatable("screen.dbzenith.racial_on_key") : Component.empty())
                    : Component.translatable("screen.dbzenith.racial_unlock", RacialSkills.unlockLevel(s));
            DbzTheme.text(g, font, state, x + CARD_W - 8 - font.width(state) * 0.65f, y + 17, open ? DbzTheme.TITLE : DbzTheme.BAD, 0.65f);
        }
        if (maxScroll() > 0) {                                  // scroll thumb
            int track = ROWS * (CARD_H + 4) - 4;
            int th = Math.max(12, track * ROWS / (ROWS + maxScroll()));
            int ty = top + 34 + (track - th) * scroll / maxScroll();
            g.fill(left + W - 6, top + 34, left + W - 4, top + 34 + track, 0x40FFFFFF);
            g.fill(left + W - 6, ty, left + W - 4, ty + th, 0xC0FFD27A);
        }

        // details of the hovered (or last clicked) skill
        RacialSkill s = hover != null ? hover : focused;
        int dy = top + 34 + ROWS * (CARD_H + 4) + 2;
        DbzTheme.divider(g, left + 10, dy, W - 20);
        if (s == null) {
            DbzTheme.wrapped(g, font, Component.translatable("screen.dbzenith.racial_help"), left + 12, dy + 6, W - 90, DbzTheme.DIM);
        } else {
            DbzTheme.text(g, font, Component.translatable(s.translationKey()), left + 12, dy + 5, s.color(), 1f);
            DbzTheme.wrapped(g, font, Component.translatable(s.descriptionKey()), left + 12, dy + 17, W - 90, DbzTheme.TEXT);
            List<Component> lines = effects(s);
            int ly = dy + 39;
            for (Component line : lines) {
                DbzTheme.text(g, font, line, left + 14, ly, DbzTheme.GOOD, 0.7f);
                ly += 8;
            }
        }
        super.render(g, mouseX, mouseY, partial);
    }

    /** One line per modifier ("+15% power while below 30% body"), and the active's ki, cooldown and duration. */
    static List<Component> effects(RacialSkill s) {
        List<Component> out = new ArrayList<>();
        for (RacialSkill.Mod m : s.mods()) {
            String amount = switch (m.stat()) {
                case KNOCKBACK_RESIST, LIFESTEAL, KI_ON_HIT -> Math.round(m.amount() * 100) + "%";
                default -> (m.amount() >= 0 ? "+" : "") + Math.round(m.amount() * 100) + "%";
            };
            out.add(Component.translatable("racial.dbzenith.mod", amount,
                    Component.translatable("racial.dbzenith.stat." + m.stat().name().toLowerCase()),
                    Component.translatable("racial.dbzenith.when." + m.when().name().toLowerCase(), s.durationTicks() / 20, s.durationTicks() / 10)));
        }
        if (s.isActive()) {
            out.add(s.durationTicks() > 0
                    ? Component.translatable("racial.dbzenith.use_timed", fmt(s.kiCostPercent()), s.cooldownTicks() / 20, s.durationTicks() / 20)
                    : Component.translatable("racial.dbzenith.use", fmt(s.kiCostPercent()), s.cooldownTicks() / 20));
        }
        return out.size() > 4 ? out.subList(0, 4) : out;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
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
