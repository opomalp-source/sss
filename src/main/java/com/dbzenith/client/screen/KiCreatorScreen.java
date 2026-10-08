package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.KiCreatorPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.skill.CustomTechniques;
import com.dbzenith.skill.CustomTechniques.Kind;
import com.dbzenith.skill.CustomTechniques.Method;
import com.dbzenith.skill.CustomTechniques.Mod;
import com.dbzenith.skill.CustomTechniques.Origin;
import com.dbzenith.skill.CustomTechniques.Spec;
import com.dbzenith.skill.Technique;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Ki Creator (v2): pick a slot, then shape a technique: its shape, how it is used (method), where it leaves the
 * body (origin), its ki type, power, modifiers and colour, and name it. The numbers at the bottom are what it will do
 * in your hands right now; ki cost and cooldown are set by the game (see {@link CustomTechniques}), so stronger
 * designs simply cost more. Method, origin and type are cycle buttons (right-click to go back).
 */
public class KiCreatorScreen extends Screen {
    private static final int W = 404, H = 236;
    private static final int[] COLORS = {0x8FD8FF, 0x3CC8FF, 0x5A6AFF, 0xB070FF, 0xFF70D0, 0xFF4040, 0xFF9A2A, 0xFFD040, 0xFFF2A0,
            0x7CFF7C, 0x40D0A0, 0xFFFFFF};

    private final Screen parent;
    private int slot;
    private Kind kind = Kind.BLAST;
    private Method method = Method.FIRED;
    private Origin origin = Origin.HAND;
    private Technique.KiType type = Technique.KiType.PURE;
    private int power = 2;
    private int mods;
    private int color = COLORS[0];
    private String name = "";
    private EditBox nameBox;

    private int left, top;

    public KiCreatorScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.ki_creator"));
        this.parent = parent;
        load(0);
    }

    private void load(int s) {
        slot = s;
        Spec spec = ClientPlayerData.get().getCustomSpec(s);
        if (spec != null) {
            kind = spec.kind();
            power = spec.power();
            mods = spec.mods();
            color = spec.color();
            name = spec.name();
            method = spec.method();
            origin = spec.origin();
            type = spec.type();
        } else {
            name = "";
        }
    }

    private Spec spec() {
        return new Spec(nameBox == null ? name : nameBox.getValue(), kind, power, mods, color, method, origin, type);
    }

    /** Keep the design valid after the shape changes: drop what no longer fits. */
    private void fit() {
        for (Mod m : Mod.values()) if (!m.fits(kind)) mods &= ~(1 << m.ordinal());
        if (!method.fits(kind)) method = Method.FIRED;
        if (!origin.fits(kind)) origin = kind == Kind.NOVA ? Origin.BODY : Origin.HAND;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        PlayerData d = ClientPlayerData.get();
        int open = CustomTechniques.slots(d);
        for (int i = 0; i < CustomTechniques.MAX_SLOTS; i++) {
            int s = i;
            Spec sp = d.getCustomSpec(i);
            Component label = i >= open ? Component.translatable("kicreator.dbzenith.slot_locked")
                    : sp == null ? Component.translatable("kicreator.dbzenith.slot_empty", i + 1) : Component.literal(sp.name());
            ThemedButton b = ThemedButton.of(label, x -> {
                load(s);
                rebuild();
            }).bounds(left + 8, top + 24 + i * 22, 108, 18).build().selected(i == slot);
            b.active = i < open;
            addRenderableWidget(b);
        }

        int ex = left + 126;
        nameBox = new EditBox(font, ex + 40, top + 20, 150, 13, Component.translatable("kicreator.dbzenith.name"));
        nameBox.setMaxLength(CustomTechniques.NAME_LENGTH);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);

        Kind[] kinds = Kind.values();                                   // shapes: two rows
        for (int i = 0; i < kinds.length; i++) {
            Kind k = kinds[i];
            addRenderableWidget(ThemedButton.of(Component.translatable("kicreator.dbzenith.kind." + k.name().toLowerCase()), b -> {
                kind = k;
                fit();
                rebuild();
            }).bounds(ex + (i % 5) * 54, top + 37 + (i / 5) * 15, 51, 13)
                    .tooltip(Tooltip.create(Component.translatable("kicreator.dbzenith.kind." + k.name().toLowerCase() + ".desc"))).build().selected(k == kind));
        }

        cycle(ex, top + 70, "method", method.name(), () -> method = next(Method.values(), method, 1, m -> m.fits(kind)),
                () -> method = next(Method.values(), method, -1, m -> m.fits(kind)));
        cycle(ex + 90, top + 70, "origin", origin.name(), () -> origin = next(Origin.values(), origin, 1, o -> o.fits(kind)),
                () -> origin = next(Origin.values(), origin, -1, o -> o.fits(kind)));
        cycle(ex + 180, top + 70, "type", type.name(), () -> type = next(Technique.KiType.values(), type, 1, t -> true),
                () -> type = next(Technique.KiType.values(), type, -1, t -> true));

        addRenderableWidget(ThemedButton.of(Component.literal("-"), b -> {
            power = Math.max(1, power - 1);
            rebuild();
        }).bounds(ex + 34, top + 86, 13, 12).build());
        addRenderableWidget(ThemedButton.of(Component.literal("+"), b -> {
            power = Math.min(5, power + 1);
            rebuild();
        }).bounds(ex + 113, top + 86, 13, 12).build());

        Mod[] all = Mod.values();                                       // modifiers: three rows of five
        int max = CustomTechniques.maxMods(d);
        for (int i = 0; i < all.length; i++) {
            Mod m = all[i];
            boolean on = (mods & 1 << m.ordinal()) != 0;
            ThemedButton b = ThemedButton.of(Component.translatable("kicreator.dbzenith.mod." + m.name().toLowerCase()), x -> {
                mods ^= 1 << m.ordinal();
                rebuild();
            }).bounds(ex + (i % 5) * 54, top + 113 + (i / 5) * 14, 51, 12)
                    .tooltip(Tooltip.create(Component.translatable("kicreator.dbzenith.mod." + m.name().toLowerCase() + ".desc"))).build().selected(on);
            b.active = m.fits(kind) && (on || Integer.bitCount(CustomTechniques.modBits(mods)) < max);
            addRenderableWidget(b);
        }
        boolean calm = (mods & CustomTechniques.CALM) != 0;                // destruction: a free toggle beside the modifiers (CX-20)
        addRenderableWidget(ThemedButton.of(Component.translatable(calm ? "kicreator.dbzenith.destruction_off" : "kicreator.dbzenith.destruction_on"), x -> {
            mods ^= CustomTechniques.CALM;
            rebuild();
        }).bounds(ex + 4 * 54, top + 113 + 2 * 14, 51, 12)
                .tooltip(Tooltip.create(Component.translatable("kicreator.dbzenith.destruction.desc"))).build().selected(!calm));

        boolean exists = d.getCustomSpec(slot) != null;
        addRenderableWidget(ThemedButton.of(Component.translatable(exists ? "kicreator.dbzenith.rewrite" : "kicreator.dbzenith.create",
                CustomTechniques.tpCost(spec())), b -> ModNetwork.sendToServer(new KiCreatorPacket(false, slot, spec())))
                .bounds(ex, top + H - 22, 130, 16).build());
        ThemedButton del = ThemedButton.of(Component.translatable("kicreator.dbzenith.delete"), b -> {
            ModNetwork.sendToServer(new KiCreatorPacket(true, slot, null));
            name = "";
        }).bounds(ex + 134, top + H - 22, 60, 16).build();
        del.active = exists;
        addRenderableWidget(del);
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.back"), b -> onClose()).bounds(left + W - 70, top + H - 22, 62, 16).build());
    }

    /** A cycle button: left click forward, right click back. */
    private ThemedButton cycle(int x, int y, String what, String value, Runnable forward, Runnable back) {
        ThemedButton b = new CycleButton(x, y, 86, 13, Component.translatable("kicreator.dbzenith." + what + "." + value.toLowerCase()), forward, back);
        b.setTooltip(Tooltip.create(Component.translatable("kicreator.dbzenith." + what + "." + value.toLowerCase() + ".desc")));
        addRenderableWidget(b);
        return b;
    }

    private final class CycleButton extends ThemedButton {
        private final Runnable back;

        CycleButton(int x, int y, int w, int h, Component label, Runnable forward, Runnable back) {
            super(x, y, w, h, label, b -> {
                forward.run();
                rebuild();
            });
            this.back = back;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 1 && active && visible && isMouseOver(mx, my)) {
                back.run();
                rebuild();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
    }

    private static <T extends Enum<T>> T next(T[] all, T current, int step, java.util.function.Predicate<T> ok) {
        int i = current.ordinal();
        for (int n = 0; n < all.length; n++) {
            i = (i + step + all.length) % all.length;
            if (ok.test(all[i])) return all[i];
        }
        return current;
    }

    private void rebuild() {
        name = nameBox == null ? name : nameBox.getValue();
        clearWidgets();
        init();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int sx = left + 126 + 150, sy = top + 87;
        for (int i = 0; i < COLORS.length; i++) {
            if (mx >= sx + i * 10 && mx < sx + i * 10 + 9 && my >= sy && my < sy + 9) {
                color = COLORS[i];
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private int seen = designsHash();

    private static int designsHash() {
        PlayerData d = ClientPlayerData.get();
        int h = 1;
        for (int i = 0; i < CustomTechniques.MAX_SLOTS; i++) h = h * 31 + java.util.Objects.hashCode(d.getCustomSpec(i));
        return h;
    }

    @Override
    public void tick() {
        super.tick();
        if (nameBox != null) nameBox.tick();
        int now = designsHash();
        if (now != seen) {                                   // the server confirmed a change: show it
            seen = now;
            rebuild();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.window(g, font, title, left, top, W, H);
        PlayerData d = ClientPlayerData.get();
        int ex = left + 126;
        DbzTheme.text(g, font, Component.translatable("kicreator.dbzenith.name"), ex, top + 23, DbzTheme.DIM, 0.8f);
        DbzTheme.text(g, font, Component.translatable("kicreator.dbzenith.power"), ex, top + 88, DbzTheme.DIM, 0.8f);
        for (int i = 0; i < 5; i++) {                                    // power pips
            int px = ex + 50 + i * 12;
            DbzTheme.slant(g, px, top + 87, 9, 9, 3, i < power ? 0xFFFFB040 : 0x60303848, i < power ? 0xFFB0400E : 0x60202430);
        }
        int sx = ex + 150, sy = top + 87;                                // colours
        for (int i = 0; i < COLORS.length; i++) {
            g.fill(sx + i * 10 - 1, sy - 1, sx + i * 10 + 10, sy + 10, COLORS[i] == color ? 0xFFFFFFFF : 0xFF404048);
            g.fill(sx + i * 10, sy, sx + i * 10 + 9, sy + 9, 0xFF000000 | COLORS[i]);
        }
        int max = CustomTechniques.maxMods(d);
        DbzTheme.text(g, font, Component.translatable("kicreator.dbzenith.mods_n", Integer.bitCount(CustomTechniques.modBits(mods)), max), ex, top + 103, DbzTheme.DIM, 0.75f);

        // what it will do, in your hands
        Technique t = CustomTechniques.build(slot, spec());
        int y = top + 157;
        DbzTheme.divider(g, ex, y - 3, 268);
        DbzTheme.icon(g, DbzTheme.ICON_ORB, ex, y, 20, 0xFF000000 | color);
        DbzTheme.text(g, font, Component.literal(CustomTechniques.cleanName(name, kind)), ex + 25, y, 0xFF000000 | color, 1f);
        Component desc = t.description();
        float ds = Math.min(0.7f, 240f / Math.max(1, font.width(desc)));
        DbzTheme.text(g, font, desc, ex + 25, y + 11, DbzTheme.DIM, ds);
        y += 24;
        double perShot = DamageCalculator.kiOutgoing(d, t.damageMult());
        String damage = t.count() > 1 ? String.format("%,.0f x %d", perShot, t.count()) : String.format("%,.0f", perShot);
        stat(g, ex, y, "kicreator.dbzenith.stat.damage", damage);
        stat(g, ex + 136, y, "kicreator.dbzenith.stat.cost", String.format("%,.0f", DamageCalculator.kiCost(d, t.kiCost())));
        stat(g, ex, y + 10, "kicreator.dbzenith.stat.cooldown", String.format("%.1fs", t.cooldownTicks() / 20.0));
        stat(g, ex + 136, y + 10, "kicreator.dbzenith.stat.speed", t.style() == Technique.Style.SELF ? "-" : String.format("%.1f", t.speed()));
        Component why = StatCalculator.level(d) < CustomTechniques.UNLOCK_LEVEL
                ? Component.translatable("technique.dbzenith.problem.level", CustomTechniques.UNLOCK_LEVEL)
                : type == Technique.KiType.DIVINE && !d.hasFlag("god_ki") ? Component.translatable("kicreator.dbzenith.problem.divine")
                : d.getTrainingPoints() < CustomTechniques.tpCost(spec()) ? Component.translatable("technique.dbzenith.problem.tp", CustomTechniques.tpCost(spec())) : null;
        g.drawString(font, Component.translatable("screen.dbzenith.tp", String.format("%,d", d.getTrainingPoints())), left + 8, top + H - 18, DbzTheme.GOOD, true);
        if (why != null) DbzTheme.text(g, font, why, ex + 200, top + H - 34, DbzTheme.BAD, 0.7f);
        super.render(g, mouseX, mouseY, partial);
    }

    private void stat(GuiGraphics g, int x, int y, String key, String value) {
        DbzTheme.text(g, font, Component.translatable(key), x, y, DbzTheme.DIM, 0.75f);
        DbzTheme.text(g, font, value, x + 64, y, DbzTheme.TEXT, 0.75f);
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
