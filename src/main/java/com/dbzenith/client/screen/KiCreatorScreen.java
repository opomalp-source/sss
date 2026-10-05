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
import com.dbzenith.skill.CustomTechniques.Mod;
import com.dbzenith.skill.CustomTechniques.Spec;
import com.dbzenith.skill.Technique;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Ki Creator: pick a slot, then shape a technique: its kind, power, up to two modifiers and colour, and name it.
 * The numbers on the right are what it will do in your hands right now; ki cost and cooldown are set by the game
 * (see {@link CustomTechniques}), so stronger designs simply cost more.
 */
public class KiCreatorScreen extends Screen {
    private static final int W = 404, H = 236;
    private static final int[] COLORS = {0x8FD8FF, 0x3CC8FF, 0x5A6AFF, 0xB070FF, 0xFF70D0, 0xFF4040, 0xFF9A2A, 0xFFD040, 0xFFF2A0,
            0x7CFF7C, 0x40D0A0, 0xFFFFFF};

    private final Screen parent;
    private int slot;
    private Kind kind = Kind.BLAST;
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
        } else {
            name = "";
        }
    }

    private Spec spec() {
        return new Spec(nameBox == null ? name : nameBox.getValue(), kind, power, mods, color);
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
        nameBox = new EditBox(font, ex + 40, top + 22, 150, 14, Component.translatable("kicreator.dbzenith.name"));
        nameBox.setMaxLength(CustomTechniques.NAME_LENGTH);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);

        Kind[] kinds = Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            Kind k = kinds[i];
            addRenderableWidget(ThemedButton.of(Component.translatable("kicreator.dbzenith.kind." + k.name().toLowerCase()), b -> {
                kind = k;
                for (Mod m : Mod.values()) if (!m.fits(k)) mods &= ~(1 << m.ordinal());
                rebuild();
            }).bounds(ex + i * 53, top + 42, 50, 15).build().selected(k == kind));
        }
        addRenderableWidget(ThemedButton.of(Component.literal("-"), b -> {
            power = Math.max(1, power - 1);
            rebuild();
        }).bounds(ex + 40, top + 62, 15, 14).build());
        addRenderableWidget(ThemedButton.of(Component.literal("+"), b -> {
            power = Math.min(5, power + 1);
            rebuild();
        }).bounds(ex + 126, top + 62, 15, 14).build());
        Mod[] all = Mod.values();
        for (int i = 0; i < all.length; i++) {
            Mod m = all[i];
            boolean on = (mods & 1 << m.ordinal()) != 0;
            ThemedButton b = ThemedButton.of(Component.translatable("kicreator.dbzenith.mod." + m.name().toLowerCase()), x -> {
                mods ^= 1 << m.ordinal();
                rebuild();
            }).bounds(ex + i * 53, top + 82, 50, 15).build().selected(on);
            b.active = m.fits(kind) && (on || Integer.bitCount(mods) < CustomTechniques.MAX_MODS);
            addRenderableWidget(b);
        }

        boolean exists = d.getCustomSpec(slot) != null;
        addRenderableWidget(ThemedButton.of(Component.translatable(exists ? "kicreator.dbzenith.rewrite" : "kicreator.dbzenith.create",
                CustomTechniques.tpCost(spec())), b -> {
            ModNetwork.sendToServer(new KiCreatorPacket(false, slot, spec()));
        }).bounds(ex, top + H - 24, 130, 18).build());
        ThemedButton del = ThemedButton.of(Component.translatable("kicreator.dbzenith.delete"), b -> {
            ModNetwork.sendToServer(new KiCreatorPacket(true, slot, null));
            name = "";
        }).bounds(ex + 134, top + H - 24, 60, 18).build();
        del.active = exists;
        addRenderableWidget(del);
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.back"), b -> onClose()).bounds(left + W - 70, top + H - 24, 62, 18).build());
    }

    private void rebuild() {
        name = nameBox == null ? name : nameBox.getValue();
        clearWidgets();
        init();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int sx = left + 166, sy = top + 103;
        for (int i = 0; i < COLORS.length; i++) {
            if (mx >= sx + i * 12 && mx < sx + i * 12 + 10 && my >= sy && my < sy + 10) {
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
        DbzTheme.text(g, font, Component.translatable("kicreator.dbzenith.name"), ex, top + 25, DbzTheme.DIM, 0.85f);
        DbzTheme.text(g, font, Component.translatable("kicreator.dbzenith.power"), ex, top + 65, DbzTheme.DIM, 0.85f);
        for (int i = 0; i < 5; i++) {                                    // power pips
            int px = ex + 60 + i * 13;
            DbzTheme.slant(g, px, top + 64, 10, 10, 3, i < power ? 0xFFFFB040 : 0x60303848, i < power ? 0xFFB0400E : 0x60202430);
        }
        DbzTheme.text(g, font, Component.translatable("kicreator.dbzenith.color"), ex, top + 104, DbzTheme.DIM, 0.85f);
        int sx = left + 166, sy = top + 103;
        for (int i = 0; i < COLORS.length; i++) {
            g.fill(sx + i * 12 - 1, sy - 1, sx + i * 12 + 11, sy + 11, COLORS[i] == color ? 0xFFFFFFFF : 0xFF404048);
            g.fill(sx + i * 12, sy, sx + i * 12 + 10, sy + 10, 0xFF000000 | COLORS[i]);
        }

        // what it will do, in your hands
        Technique t = CustomTechniques.build(slot, spec());
        int y = top + 122;
        DbzTheme.divider(g, ex, y - 4, 268);
        DbzTheme.icon(g, DbzTheme.ICON_ORB, ex, y, 24, 0xFF000000 | color);
        DbzTheme.text(g, font, Component.literal(CustomTechniques.cleanName(name, kind)), ex + 30, y + 1, 0xFF000000 | color, 1f);
        DbzTheme.text(g, font, t.description(), ex + 30, y + 12, DbzTheme.DIM, 0.75f);
        y += 30;
        double perShot = DamageCalculator.kiOutgoing(d, t.damageMult());
        String damage = t.count() > 1 ? String.format("%,.0f x %d", perShot, t.count()) : String.format("%,.0f", perShot);
        stat(g, ex, y, "kicreator.dbzenith.stat.damage", damage);
        stat(g, ex + 136, y, "kicreator.dbzenith.stat.cost", String.format("%,.0f", DamageCalculator.kiCost(d, t.kiCost())));
        stat(g, ex, y + 11, "kicreator.dbzenith.stat.cooldown", String.format("%.1fs", t.cooldownTicks() / 20.0));
        stat(g, ex + 136, y + 11, "kicreator.dbzenith.stat.speed", String.format("%.1f", t.speed()));
        DbzTheme.wrapped(g, font, Component.translatable("kicreator.dbzenith.kind." + kind.name().toLowerCase() + ".desc"), ex, y + 26, 268, DbzTheme.TEXT);
        Component why = StatCalculator.level(d) < CustomTechniques.UNLOCK_LEVEL
                ? Component.translatable("technique.dbzenith.problem.level", CustomTechniques.UNLOCK_LEVEL)
                : d.getTrainingPoints() < CustomTechniques.tpCost(spec()) ? Component.translatable("technique.dbzenith.problem.tp", CustomTechniques.tpCost(spec())) : null;
        g.drawString(font, Component.translatable("screen.dbzenith.tp", String.format("%,d", d.getTrainingPoints())), left + 8, top + H - 40, DbzTheme.GOOD, true);
        if (why != null) DbzTheme.text(g, font, why, ex, top + H - 34, DbzTheme.BAD, 0.8f);
        super.render(g, mouseX, mouseY, partial);
    }

    private void stat(GuiGraphics g, int x, int y, String key, String value) {
        DbzTheme.text(g, font, Component.translatable(key), x, y, DbzTheme.DIM, 0.8f);
        DbzTheme.text(g, font, value, x + 64, y, DbzTheme.TEXT, 0.8f);
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
