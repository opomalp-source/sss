package com.dbzenith.client.screen;

import com.dbzenith.client.ModKeys;
import com.dbzenith.client.Prediction;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.combat.engine.Move;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Controls and move list (CX-19 phase 8): the combat keys as you have them bound, every melee move with its input and
 * frame data (read from the move files the server sent, so data-pack moves show up too), and how the Dash key, guard,
 * ki and lock-on work. Scrolls with the mouse wheel.
 */
public class MoveListScreen extends Screen {
    private static final int MAX_W = 460, H = 250, LINE = 11;
    private int W = MAX_W;

    private enum Tab { CONTROLS, MELEE, DEFENSE, KI }

    private record Line(Component name, Component input, Component detail, int color) {}

    private final Screen parent;
    private Tab tab = Tab.CONTROLS;
    private int left, top;
    private double scroll;

    public MoveListScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.move_list"));
        this.parent = parent;
    }

    /** Dev: open on a tab (0 controls, 1 melee, 2 dodge and guard, 3 ki). */
    public MoveListScreen onTab(int i) {
        tab = Tab.values()[Math.max(0, Math.min(Tab.values().length - 1, i))];
        return this;
    }

    private static Component key(KeyMapping k) {
        return k.getTranslatedKeyMessage().copy().withStyle(net.minecraft.ChatFormatting.YELLOW);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("movelist.dbzenith." + key, args);
    }

    private List<Line> lines() {
        List<Line> out = new ArrayList<>();
        var o = minecraft.options;
        switch (tab) {
            case CONTROLS -> {
                KeyMapping[][] rows = {{o.keyAttack}, {ModKeys.HEAVY}, {ModKeys.DASH}, {ModKeys.GUARD}, {ModKeys.KI_BLAST}, {ModKeys.KI_ATTACK},
                        {ModKeys.NEXT_TECHNIQUE}, {ModKeys.LOCK_ON}, {ModKeys.PVP}, {ModKeys.CHARGE}, {ModKeys.FLY}, {ModKeys.TRANSFORM}};
                String[] what = {"attack", "heavy", "dash", "guard", "ki_blast", "technique", "next_technique", "lock_on", "pvp", "charge", "fly", "transform"};
                for (int i = 0; i < rows.length; i++) out.add(new Line(t("key." + what[i]), key(rows[i][0]), t("key." + what[i] + ".how"), DbzTheme.ACCENT));
            }
            case MELEE -> {
                List<Move> moves = new ArrayList<>(Prediction.moves());
                moves.sort(Comparator.comparing((Move m) -> !m.after.contains("start") && m.after.stream().noneMatch(a -> Prediction.moves().stream().anyMatch(x -> x.id.equals(a))))
                        .thenComparing(m -> m.button).thenComparing(m -> m.id));                  // the chain first, specials last
                if (moves.isEmpty()) out.add(new Line(t("no_moves"), Component.empty(), Component.empty(), DbzTheme.DIM));
                for (Move m : moves) out.add(new Line(name(m), input(m), detail(m), m.button == Move.Button.HEAVY ? 0xFFFF7A4A : DbzTheme.ACCENT));
            }
            case DEFENSE -> {
                Component dash = key(ModKeys.DASH), guard = key(ModKeys.GUARD), attack = key(o.keyAttack);
                for (String s : new String[]{"dash", "vanish", "super_dash", "chase", "burst", "air_recover", "tech_roll", "side_step"}) {
                    out.add(new Line(t("defense." + s), t("defense." + s + ".input", dash, guard), t("defense." + s + ".how"), 0xFF7CE0FF));
                }
                for (String s : new String[]{"guard", "perfect_guard", "guard_break", "counter"}) {
                    out.add(new Line(t("defense." + s), t("defense." + s + ".input", guard, attack), t("defense." + s + ".how"), DbzTheme.STAMINA));
                }
            }
            case KI -> {
                Component blast = key(ModKeys.KI_BLAST), tech = key(ModKeys.KI_ATTACK), next = key(ModKeys.NEXT_TECHNIQUE), lock = key(ModKeys.LOCK_ON);
                for (String s : new String[]{"quick_blast", "charged_blast", "technique", "next", "super", "ultimate", "beam_struggle", "surge", "lock_on", "lock_next"}) {
                    out.add(new Line(t("ki." + s), t("ki." + s + ".input", blast, tech, next, lock), t("ki." + s + ".how"), DbzTheme.KI));
                }
            }
        }
        return out;
    }

    /** A move's name: move.dbzenith.<id> if there is one, else its id made readable. */
    public static Component nameOf(Move m) {
        return name(m);
    }

    static Component name(Move m) {
        String id = m.id.contains(":") ? m.id.substring(m.id.indexOf(':') + 1) : m.id;
        String pretty = id.replace('_', ' ');
        return Component.translatableWithFallback("move.dbzenith." + id, Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1));
    }

    /** How to throw it: the button, the push, where, and what it follows. */
    private Component input(Move m) {
        var mc = minecraft.options;
        Component button = key(m.button == Move.Button.HEAVY ? ModKeys.HEAVY : mc.keyAttack);
        Component out = Component.empty().append(button);
        switch (m.dir) {
            case FORWARD, BACK, SIDE, UP, DOWN, NEUTRAL -> out = out.copy().append(" ").append(t("dir." + m.dir.name().toLowerCase()));
            default -> { }
        }
        if (m.where != Move.Where.ANY) out = out.copy().append(" ").append(t("where." + m.where.name().toLowerCase()));
        List<String> after = m.after.stream().filter(a -> !a.equals("start")).toList();
        if (!after.isEmpty()) {
            Component chain = Component.empty();
            for (int i = 0; i < after.size(); i++) {
                String prevId = after.get(i);
                Move prev = Prediction.moves().stream().filter(x -> x.id.equals(prevId)).findFirst().orElse(null);
                chain = chain.copy().append(i > 0 ? ", " : "").append(prev == null ? Component.literal(prevId) : name(prev));
                if (i == 2 && after.size() > 3) {
                    chain = chain.copy().append(", …");
                    break;
                }
            }
            out = out.copy().append(" ").append(t(m.after.contains("start") ? "after_or_open" : "after", chain));
        } else if (!m.after.contains("start")) {
            out = out.copy().append(" ").append(t("special"));
        }
        return out;
    }

    /** Frame data and what it does. */
    private static Component detail(Move m) {
        Component d = t("frames", m.startup, m.active, m.recovery, String.format("%.2f", m.damage));
        if (m.launch != Move.Launch.NONE) d = d.copy().append(" · ").append(t("launch." + m.launch.name().toLowerCase()));
        if (m.unblockable) d = d.copy().append(" · ").append(t("unblockable"));
        if (m.armor > 0) d = d.copy().append(" · ").append(t("armor", m.armor));
        return d;
    }

    @Override
    protected void init() {
        W = Math.min(MAX_W, width - 12);
        left = (width - W) / 2;
        top = (height - H) / 2;
        Tab[] tabs = Tab.values();
        int tw = (W - 16 - (tabs.length - 1) * 4) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab tb = tabs[i];
            addRenderableWidget(ThemedButton.of(t("tab." + tb.name().toLowerCase()), b -> {
                tab = tb;
                scroll = 0;
                rebuild();
            }).bounds(left + 8 + i * (tw + 4), top + 16, tw, 16).build().selected(tb == tab));
        }
        addRenderableWidget(ThemedButton.of(Component.translatable("settings.dbzenith.keybinds"),
                b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(left + 8, top + H - 24, 130, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - 78, top + H - 24, 70, 18).build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private int viewTop() {
        return top + 42;
    }

    private int viewHeight() {
        return H - 42 - 30;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - delta * LINE * 2, 0, Math.max(0, lines().size() * LINE * 2 - viewHeight()));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.window(g, font, title, left, top, W, H);
        DbzTheme.divider(g, left + 8, top + 38, W - 16);
        g.enableScissor(left + 6, viewTop(), left + W - 6, viewTop() + viewHeight());
        int y = viewTop() + 2 - (int) scroll;
        int col2 = left + 12 + 108;
        for (Line l : lines()) {
            if (y > viewTop() - LINE * 2 && y < viewTop() + viewHeight()) {
                DbzTheme.text(g, font, l.name, left + 12, y, l.color, 0.8f);
                DbzTheme.text(g, font, l.input, col2, y, DbzTheme.TEXT, 0.8f);
                DbzTheme.text(g, font, l.detail, col2, y + LINE - 1, DbzTheme.DIM, 0.56f);
            }
            y += LINE * 2;
        }
        g.disableScissor();
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
