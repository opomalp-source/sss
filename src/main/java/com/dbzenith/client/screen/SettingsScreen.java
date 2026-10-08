package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.client.ui.ThemedSlider;
import com.dbzenith.config.DBZConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Dragon Block Zenith settings, in the mod's own look: HUD, effects, camera and controls, each option with a tooltip
 * explaining it. Changes apply at once and are saved to the client config file.
 */
public class SettingsScreen extends Screen {
    private static final int W = 360, H = 222, ROW = 22;

    private enum Tab { HUD, STYLE, EFFECTS, CAMERA, COMBAT, CONTROLS }

    /** One setting: what it is, how it is shown and changed. */
    private record Option(String key, ForgeConfigSpec.ConfigValue<?> value, double min, double max, String[] choices, String unit) {
        Option(String key, ForgeConfigSpec.ConfigValue<?> value, double min, double max, String[] choices) {
            this(key, value, min, max, choices, "%");
        }
    }

    private final Screen parent;
    private Tab tab = Tab.HUD;
    private int left, top;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.settings"));
        this.parent = parent;
    }

    /** Dev: open on a tab by name (hud, style, effects, camera, combat, controls). */
    public SettingsScreen onTab(String name) {
        for (Tab t : Tab.values()) if (t.name().equalsIgnoreCase(name)) tab = t;
        return this;
    }

    private static List<Option> options(Tab tab) {
        DBZConfig.Client c = DBZConfig.CLIENT;
        List<Option> o = new ArrayList<>();
        switch (tab) {
            case HUD -> {
                o.add(new Option("hud_style", c.hudStyle, 0, 4, new String[]{"saga", "classic", "minimal", "ornate", "clean"}));
                o.add(new Option("hud_scale", c.hudScale, 0.6, 1.4, null));
                o.add(new Option("custom_hotbar", c.customHotbar, 0, 0, null));
                o.add(new Option("hide_hearts", c.hideVanillaHearts, 0, 0, null));
                o.add(new Option("cut_in", c.transformCutIn, 0, 0, null));
                o.add(new Option("debug_stats", c.showDebugOverlay, 0, 0, null));
            }
            case EFFECTS -> {
                o.add(new Option("aura_detail", c.auraDetail, 0, 2, new String[]{"low", "normal", "high"}));
                o.add(new Option("aura_renderer", c.auraRenderer, 0, 2, new String[]{"auto", "shader", "plain"}));
                o.add(new Option("first_person_aura", c.firstPersonAura, 0, 0, null));
                o.add(new Option("afterimages", c.afterimages, 0, 0, null));
                o.add(new Option("hitstop", c.hitstop, 0, 0, null));
                o.add(new Option("hair_physics", c.hairPhysics, 0, 0, null));
                o.add(new Option("procedural_motion", c.proceduralMotion, 0, 0, null));
            }
            case CAMERA -> {
                o.add(new Option("screen_shake", c.screenShake, 0, 2, null));
                o.add(new Option("fov_effects", c.fovEffects, 0, 0, null));
                o.add(new Option("speed_lines", c.speedLines, 0, 0, null));
                o.add(new Option("lock_camera", c.lockOnCameraSpeed, 0, 3, null));                // lock-on (CX-19)
                o.add(new Option("lock_free_look", c.lockOnFreeLook, 0, 90, null, "\u00b0"));
                o.add(new Option("lock_range", c.lockOnRange, 8, 80, null, "m"));
                o.add(new Option("pvp_camera", c.pvpCamera, 0, 0, null));                         // CX-20
                o.add(new Option("shoulder_offset", c.shoulderOffset, 0, 3, null, "b"));
            }
            case COMBAT -> {                                                    // CX-19
                o.add(new Option("damage_popups", c.damagePopups, 0, 0, null));
                o.add(new Option("callouts", c.combatCallouts, 0, 0, null));
                o.add(new Option("enemy_panel", c.enemyPanel, 0, 0, null));
                o.add(new Option("combo_counter", c.comboCounter, 0, 0, null));
                o.add(new Option("ultimate_cinematic", c.ultimateCinematic, 0, 0, null));
                o.add(new Option("prediction", c.prediction, 0, 0, null));
                o.add(new Option("combat_log", c.combatLog, 0, 0, null));
                o.add(new Option("hitbox_overlay", c.hitboxOverlay, 0, 0, null));
            }
            case STYLE -> {
                o.add(new Option("ui_style", c.uiStyle, 0, 1, new String[]{"zenith", "classic"}));
                o.add(new Option("art_style", c.artStyle, 0, 2, new String[]{"painted", "hd", "classic"}));
                o.add(new Option("crosshair_out", c.crosshairStyle, 0, 2, new String[]{"star", "dot", "vanilla"}));   // CX-20
                o.add(new Option("crosshair_dot", c.crosshairDotMode, 0, 1, new String[]{"pvp", "always"}));
                o.add(new Option("crosshair_size", c.crosshairSize, 1, 8, new String[]{"1", "2", "3", "4", "5", "6", "7", "8"}));
                o.add(new Option("crosshair_color", c.crosshairColor, 0, 6, com.dbzenith.client.ui.Crosshair.COLOR_NAMES));
                o.add(new Option("crosshair_opacity", c.crosshairOpacity, 0, 1, null));
            }
            case CONTROLS -> { }
        }
        return o;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        Tab[] tabs = Tab.values();
        int tw = (W - 16 - (tabs.length - 1) * 4) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addRenderableWidget(ThemedButton.of(Component.translatable("settings.dbzenith.tab." + t.name().toLowerCase()), b -> {
                tab = t;
                rebuild();
            }).bounds(left + 8 + i * (tw + 4), top + 16, tw, 16).build().selected(t == tab));
        }

        int y = top + 44;
        int wx = left + W - 158;
        for (Option opt : options(tab)) {
            Tooltip tip = Tooltip.create(Component.translatable("settings.dbzenith." + opt.key + ".desc"));
            if (opt.value instanceof ForgeConfigSpec.BooleanValue bool) {
                ThemedButton b = ThemedButton.of(onOff(bool.get()), x -> {
                    bool.set(!bool.get());
                    save();
                    x.setMessage(onOff(bool.get()));
                    ((ThemedButton) x).selected(bool.get());
                }).bounds(wx, y, 150, 16).tooltip(tip).build().selected(bool.get());
                addRenderableWidget(b);
            } else if (opt.value instanceof ForgeConfigSpec.DoubleValue dbl) {
                ThemedSlider s = new ThemedSlider(wx, y, 150, 16, Component.empty(), (dbl.get() - opt.min) / (opt.max - opt.min)) {
                    {
                        updateMessage();
                    }

                    @Override
                    protected void updateMessage() {
                        double v = opt.min + value * (opt.max - opt.min);
                        setMessage(Component.literal(opt.unit.equals("%") ? String.format("%.0f%%", v * 100)
                                : opt.unit.equals("m") ? Component.translatable("settings.dbzenith.blocks", Math.round(v)).getString()
                                : opt.unit.equals("b") ? Component.translatable("settings.dbzenith.blocks", String.format("%.1f", v)).getString() : Math.round(v) + opt.unit));
                    }

                    @Override
                    protected void applyValue() {
                        double v = opt.min + value * (opt.max - opt.min);
                        dbl.set(opt.unit.equals("%") ? Math.round(v * 100) / 100.0 : opt.unit.equals("b") ? Math.round(v * 10) / 10.0 : Math.round(v));
                        save();
                    }
                };
                s.setTooltip(tip);
                addRenderableWidget(s);
            } else if (opt.value instanceof ForgeConfigSpec.IntValue in && opt.choices != null) {
                ThemedButton b = ThemedButton.of(choice(opt, in.get()), x -> {
                    int lo = (int) opt.min;                                   // choices count from the option's minimum
                    in.set((in.get() - lo + 1) % opt.choices.length + lo);
                    save();
                    x.setMessage(choice(opt, in.get()));
                }).bounds(wx, y, 150, 16).tooltip(tip).build();
                addRenderableWidget(b);
            }
            y += row(tab);
        }
        if (tab == Tab.CONTROLS) {
            addRenderableWidget(ThemedButton.of(Component.translatable("settings.dbzenith.keybinds"),
                    b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(left + W / 2 - 90, top + 50, 180, 18).build());
            addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.move_list"),                // CX-19 phase 8
                    b -> minecraft.setScreen(new MoveListScreen(this))).bounds(left + W / 2 - 90, top + 74, 180, 18).build());
        }

        addRenderableWidget(ThemedButton.of(Component.translatable("settings.dbzenith.reset"), b -> {
            for (Option o : options(tab)) reset(o.value);
            save();
            rebuild();
        }).bounds(left + 8, top + H - 24, 110, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - 78, top + H - 24, 70, 18).build());
    }

    /** Row spacing: tighter for a tab with many options, so they fit above the buttons. */
    private static int row(Tab tab) {
        return options(tab).size() > 6 ? 19 : ROW;
    }

    private static <T> void reset(ForgeConfigSpec.ConfigValue<T> v) {
        v.set(v.getDefault());
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    private static Component choice(Option o, int i) {
        String c = o.choices[Math.max(0, Math.min(o.choices.length - 1, i - (int) o.min))];
        return Component.translatableWithFallback("settings.dbzenith." + o.key + "." + c, c);
    }

    private static void save() {
        DBZConfig.CLIENT_SPEC.save();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.window(g, font, title, left, top, W, H);
        int y = top + 48;
        for (Option opt : options(tab)) {
            g.drawString(font, Component.translatable("settings.dbzenith." + opt.key), left + 12, y, DbzTheme.TEXT, true);
            y += row(tab);
        }
        if (tab == Tab.CONTROLS) {
            DbzTheme.text(g, font, Component.translatable("settings.dbzenith.move_list_hint"), left + 12, top + 100, DbzTheme.DIM, 0.75f);
        }
        DbzTheme.divider(g, left + 8, top + 38, W - 16);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
