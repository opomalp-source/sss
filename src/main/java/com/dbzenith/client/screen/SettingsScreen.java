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

    private enum Tab { HUD, EFFECTS, CAMERA, CONTROLS }

    /** One setting: what it is, how it is shown and changed. */
    private record Option(String key, ForgeConfigSpec.ConfigValue<?> value, double min, double max, String[] choices) {}

    private final Screen parent;
    private Tab tab = Tab.HUD;
    private int left, top;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.settings"));
        this.parent = parent;
    }

    private static List<Option> options(Tab tab) {
        DBZConfig.Client c = DBZConfig.CLIENT;
        List<Option> o = new ArrayList<>();
        switch (tab) {
            case HUD -> {
                o.add(new Option("hud_scale", c.hudScale, 0.6, 1.4, null));
                o.add(new Option("custom_hotbar", c.customHotbar, 0, 0, null));
                o.add(new Option("hide_hearts", c.hideVanillaHearts, 0, 0, null));
                o.add(new Option("cut_in", c.transformCutIn, 0, 0, null));
                o.add(new Option("debug_stats", c.showDebugOverlay, 0, 0, null));
            }
            case EFFECTS -> {
                o.add(new Option("aura_detail", c.auraDetail, 0, 2, new String[]{"low", "normal", "high"}));
                o.add(new Option("first_person_aura", c.firstPersonAura, 0, 0, null));
                o.add(new Option("afterimages", c.afterimages, 0, 0, null));
                o.add(new Option("hitstop", c.hitstop, 0, 0, null));
                o.add(new Option("hair_physics", c.hairPhysics, 0, 0, null));
                o.add(new Option("procedural_motion", c.proceduralMotion, 0, 0, null));
            }
            case CAMERA -> {
                o.add(new Option("screen_shake", c.screenShake, 0, 2, null));
                o.add(new Option("fov_effects", c.fovEffects, 0, 0, null));
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
                        setMessage(Component.literal(String.format("%.0f%%", (opt.min + value * (opt.max - opt.min)) * 100)));
                    }

                    @Override
                    protected void applyValue() {
                        dbl.set(Math.round((opt.min + value * (opt.max - opt.min)) * 100) / 100.0);
                        save();
                    }
                };
                s.setTooltip(tip);
                addRenderableWidget(s);
            } else if (opt.value instanceof ForgeConfigSpec.IntValue in && opt.choices != null) {
                ThemedButton b = ThemedButton.of(choice(opt, in.get()), x -> {
                    in.set((in.get() + 1) % opt.choices.length);
                    save();
                    x.setMessage(choice(opt, in.get()));
                }).bounds(wx, y, 150, 16).tooltip(tip).build();
                addRenderableWidget(b);
            }
            y += ROW;
        }
        if (tab == Tab.CONTROLS) {
            addRenderableWidget(ThemedButton.of(Component.translatable("settings.dbzenith.keybinds"),
                    b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(left + W / 2 - 90, top + 50, 180, 18).build());
        }

        addRenderableWidget(ThemedButton.of(Component.translatable("settings.dbzenith.reset"), b -> {
            for (Option o : options(tab)) reset(o.value);
            save();
            rebuild();
        }).bounds(left + 8, top + H - 24, 110, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - 78, top + H - 24, 70, 18).build());
    }

    private static <T> void reset(ForgeConfigSpec.ConfigValue<T> v) {
        v.set(v.getDefault());
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    private static Component choice(Option o, int i) {
        return Component.translatable("settings.dbzenith." + o.key + "." + o.choices[Math.max(0, Math.min(o.choices.length - 1, i))]);
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
            y += ROW;
        }
        if (tab == Tab.CONTROLS) {
            DbzTheme.text(g, font, Component.translatable("settings.dbzenith.moves"), left + 12, top + 74, DbzTheme.TITLE, 0.8f);
            String[] moves = {"zhit", "directional", "sweep", "chase", "chase_counter", "revenge", "breaker", "recover", "dodge", "clash", "downed"};
            for (int i = 0; i < moves.length; i++) {                     // the combat moves, how to do each
                int my = top + 84 + i * 9;
                DbzTheme.text(g, font, Component.translatable("settings.dbzenith.move." + moves[i]), left + 14, my, DbzTheme.ACCENT, 0.7f);
                DbzTheme.text(g, font, Component.translatable("settings.dbzenith.move." + moves[i] + ".how"), left + 96, my, DbzTheme.TEXT, 0.7f);
            }
        }
        DbzTheme.divider(g, left + 8, top + 38, W - 16);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
