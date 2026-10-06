package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
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
 * The Skills page of the character menu (UI v3): your race's skills, or the universal ones learned with TP, as cards
 * (an emblem in the skill's colour, ringed for actives, with the cooldown sweeping over it), what unlocks or levels
 * each, and the hovered or chosen one's details below. Click an unlocked active to put it on its key.
 */
public class RacialScreen extends MenuScreen {
    private static final int CARD_H = 28, COLS = 2;
    private int scroll;
    private RacialSkill focused;
    /** The universal (learned) skills instead of the racial ones. */
    private boolean universal;
    private UiButton learn;

    public RacialScreen(Screen parent) {
        this(parent, false);
    }

    public RacialScreen(Screen parent, boolean universal) {
        super(Component.translatable("screen.dbzenith.racial"), Page.SKILLS, parent);
        this.universal = universal;
    }

    private List<RacialSkill> skills() {
        PlayerData d = ClientPlayerData.get();
        return universal ? RacialSkills.universal() : RacialSkills.forCharacter(d.getRace(), d.getVariant());
    }

    private int gridY() { return cardY + 26; }
    private int cardW() { return (cardW - 16 - 6) / COLS; }
    private int rows() { return Math.max(2, (cardH - 26 - 62) / (CARD_H + 4)); }

    @Override
    protected void initPage() {
        PlayerData d = ClientPlayerData.get();
        Variant v = d.getVariant();
        Component who = Component.translatable(v.kind() == Variant.Kind.DEFAULT ? d.getRace().translationKey() : v.translationKey());
        Component racial = Component.translatable("screen.dbzenith.racial_title", who);
        int w1 = (int) (font.width(racial) * 0.85f) + 14;
        addRenderableWidget(UiButton.of(UiButton.Style.CHIP, racial, cardX + 8, cardY + 7, w1, 14, b -> tab(false)).selected(!universal));
        Component uni = Component.translatable("screen.dbzenith.tab_universal");
        addRenderableWidget(UiButton.of(UiButton.Style.CHIP, uni, cardX + 12 + w1, cardY + 7, (int) (font.width(uni) * 0.85f) + 14, 14,
                b -> tab(true)).selected(universal));
        learn = addRenderableWidget(UiButton.of(UiButton.Style.PRIMARY, Component.empty(), cardX + cardW - 8 - 150, cardY + cardH - 21, 150, 15, b -> {
            if (focused != null) ModNetwork.sendToServer(new RacialPackets.Learn(focused.id()));
        }).textScale(0.8f));
        learn.visible = false;
    }

    private void tab(boolean u) {
        universal = u;
        scroll = 0;
        focused = null;
        rebuildWidgets();
    }

    private int maxScroll() {
        int rows = (skills().size() + COLS - 1) / COLS;
        return Math.max(0, rows - rows());
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
            if (row < 0 || row >= rows()) continue;
            int x = cardX + 8 + col * (cardW() + 6), y = gridY() + row * (CARD_H + 4);
            if (mx >= x && mx < x + cardW() && my >= y && my < y + CARD_H) return all.get(i);
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
                if (s.learned()) {
                    ModNetwork.sendToServer(new RacialPackets.SelectSkill(s.id()));
                    d.setSkillSelected(s.id());
                } else {
                    ModNetwork.sendToServer(new RacialPackets.Select(s.id()));
                    d.setRacialSelected(s.id());                   // at once; the server's sync confirms it
                }
            }
            com.dbzenith.client.ClientSounds.uiClick();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial) {
        PlayerData d = ClientPlayerData.get();
        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        RacialSkill hover = at(mouseX, mouseY);
        List<RacialSkill> all = skills();
        int cw = cardW();
        for (int i = 0; i < all.size(); i++) {
            int row = i / COLS - scroll, col = i % COLS;
            if (row < 0 || row >= rows()) continue;
            RacialSkill s = all.get(i);
            int x = cardX + 8 + col * (cw + 6), y = gridY() + row * (CARD_H + 4);
            boolean open = RacialSkills.unlocked(d, s), selected = s.id().equals(s.learned() ? d.getSkillSelected() : d.getRacialSelected());
            boolean hot = s == hover || s == focused;
            int c = 0xFF000000 | s.color();
            Ui.tile(g, x, y, cw, CARD_H, selected, hot);
            if (open) g.fillGradient(x + 1, y + 1, x + 34, y + CARD_H - 1, DbzTheme.withAlpha(c, 60), 0x00000000);
            int ex = x + 15, ey = y + CARD_H / 2, ec = open ? c : 0xFF505058;             // the emblem
            DbzTheme.quad(g, ex, ey - 8, ex + 8, ey, ex, ey + 8, ex - 8, ey, 0xFF07070C, 0xFF07070C, 0xFF07070C, 0xFF07070C);
            DbzTheme.quad(g, ex, ey - 6, ex + 6, ey, ex, ey + 6, ex - 6, ey, DbzTheme.brighten(ec, 1.3f), ec, DbzTheme.darken(ec, 0.6f), ec);
            if (s.isActive()) DbzTheme.arc(g, ex, ey, 9f, 10.2f, 0, 360, ec, ec);
            if (s.isActive() && open && now < d.getRacialCooldown(s.id())) {
                float rest = (d.getRacialCooldown(s.id()) - now) / (float) Math.max(1, s.cooldownTicks());
                DbzTheme.arc(g, ex, ey, 0, 7, -90, 360 * rest, 0xB0000000, 0xB0000000);
            }
            Ui.text(g, font, Component.translatable(s.translationKey()), x + 29, y + 5, open ? Ui.TEXT : Ui.MUTED, 0.85f);
            Component tag = Component.translatable(s.isActive() ? "screen.dbzenith.racial_active" : "screen.dbzenith.racial_passive");
            Ui.text(g, font, Component.literal(tag.getString().toUpperCase()), x + 29, y + 16, open ? DbzTheme.brighten(c, 1.15f) : 0xFF606068, 0.6f);
            Component state;
            if (s.learned()) {
                int lv = d.getSkillLevel(s.id());
                state = lv > 0 ? Component.translatable(selected ? "screen.dbzenith.skill_level_key" : "screen.dbzenith.skill_level", lv, s.maxLevel())
                        : Component.translatable("screen.dbzenith.skill_learnable", s.tpCost(1));
            } else {
                state = open ? (selected ? Component.translatable("screen.dbzenith.racial_on_key") : Component.empty())
                        : Component.translatable("screen.dbzenith.racial_unlock", RacialSkills.unlockLevel(s));
            }
            Ui.text(g, font, state, x + cw - 6 - font.width(state) * 0.65f, y + 16, open ? Ui.GOLD : s.learned() ? Ui.MUTED : 0xFFE07068, 0.65f);
        }
        if (maxScroll() > 0) {                                                            // the scroll thumb
            int track = rows() * (CARD_H + 4) - 4, th = Math.max(12, track * rows() / (rows() + maxScroll()));
            int ty = gridY() + (track - th) * scroll / maxScroll();
            Ui.round(g, cardX + cardW - 5, ty, 3, th, 1, 0xA0FFFFFF);
        }

        // learning the next level of the chosen universal skill
        learn.visible = focused != null && focused.learned();
        int dy = gridY() + rows() * (CARD_H + 4);
        if (learn.visible) {
            int next = d.getSkillLevel(focused.id()) + 1;
            Component problem = RacialSkills.learnProblem(d, focused);
            learn.active = problem == null;
            learn.setMessage(next > focused.maxLevel() ? Component.translatable("screen.dbzenith.skill_mastered")
                    : Component.translatable("screen.dbzenith.skill_learn", next, String.format("%,d", focused.tpCost(next))));
            if (problem != null && next <= focused.maxLevel()) {
                Ui.text(g, font, problem, cardX + cardW - 8 - font.width(problem) * 0.65f, cardY + cardH - 30, 0xFFE07068, 0.65f);
            }
        }
        // the hovered (or last clicked) skill, or the help
        g.fill(cardX + 8, dy, cardX + cardW - 8, dy + 1, Ui.LINE_SOFT);
        RacialSkill s = hover != null ? hover : focused;
        int textW = cardW - 16 - (learn.visible ? 158 : 0);
        if (s == null) {
            Component sub = universal ? Component.translatable("screen.dbzenith.universal_sub", String.format("%,d", d.getTrainingPoints()))
                    : Component.translatable("screen.dbzenith.racial_sub", StatCalculator.level(d));
            int h = Ui.paragraph(g, font, sub, cardX + 8, dy + 5, textW, 0xFFC8CEDC, 0.75f, 2);
            Ui.paragraph(g, font, Component.translatable(universal ? "screen.dbzenith.universal_help" : "screen.dbzenith.racial_help"),
                    cardX + 8, dy + 7 + h, textW, Ui.MUTED, 0.7f, 3);
        } else {
            Ui.text(g, font, Component.translatable(s.translationKey()), cardX + 8, dy + 5, 0xFF000000 | s.color(), 0.9f);
            int h = Ui.paragraph(g, font, Component.translatable(s.descriptionKey()), cardX + 8, dy + 15, textW, 0xFFC8CEDC, 0.72f, 3);
            int ly = dy + 17 + h;
            for (Component line : effects(s)) {
                if (ly > cardY + cardH - 8) break;
                Ui.text(g, font, line, cardX + 10, ly, 0xFF8CE08C, 0.65f);
                ly += 7;
            }
        }
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
}
