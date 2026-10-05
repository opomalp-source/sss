package com.dbzenith.client.ui;

import com.dbzenith.client.ClientCombatState;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ModKeys;
import com.dbzenith.client.screen.DeckScreen;
import com.dbzenith.client.screen.FormScreen;
import com.dbzenith.client.screen.LifeScreen;
import com.dbzenith.client.screen.StatScreen;
import com.dbzenith.network.InputPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hold the radial key: an inner ring of actions (transform, power down, flight, overdrive, menus) and an outer ring
 * of your deck's techniques. Point with the mouse; release the key (or click) to use the slice under the pointer.
 * The game keeps running underneath.
 */
public class RadialMenuScreen extends Screen {
    private static final float INNER_R0 = 34, INNER_R1 = 84, OUTER_R0 = 90, OUTER_R1 = 122;

    private record Slice(Component label, Component hint, int icon, int tint, Runnable action) {}

    private final List<Slice> inner = new ArrayList<>();
    private final List<Slice> outer = new ArrayList<>();
    private Slice hovered;
    private boolean devHold;
    private int devHover = -1;
    private final long openedAt = System.nanoTime();

    public RadialMenuScreen() {
        super(Component.translatable("screen.dbzenith.radial"));
    }

    @Override
    protected void init() {
        inner.clear();
        outer.clear();
        inner.add(new Slice(tr("transform"), tr("transform.hint"), DbzTheme.ICON_TRANSFORM, -1, () -> send(InputPacket.Action.TRANSFORM_UP)));
        inner.add(new Slice(tr("power_down"), tr("power_down.hint"), DbzTheme.ICON_POWER_DOWN, -1, () -> send(InputPacket.Action.TRANSFORM_DOWN)));
        inner.add(new Slice(tr("fly"), tr("fly.hint"), DbzTheme.ICON_FLY, -1, () -> send(InputPacket.Action.TOGGLE_FLIGHT)));
        inner.add(new Slice(tr("overdrive"), tr("overdrive.hint"), DbzTheme.ICON_OVERDRIVE, -1, () -> send(InputPacket.Action.OVERDRIVE_UP)));
        inner.add(new Slice(tr("overdrive_off"), tr("overdrive_off.hint"), DbzTheme.ICON_OVERDRIVE_OFF, -1, () -> send(InputPacket.Action.OVERDRIVE_OFF)));
        inner.add(new Slice(tr("stats"), tr("stats.hint"), DbzTheme.ICON_STATS, -1, () -> minecraft.setScreen(new StatScreen())));
        inner.add(new Slice(tr("forms"), tr("forms.hint"), DbzTheme.ICON_FORMS, -1, () -> minecraft.setScreen(new FormScreen(null))));
        inner.add(new Slice(tr("techniques"), tr("techniques.hint"), DbzTheme.ICON_TECHNIQUES, -1, () -> minecraft.setScreen(new DeckScreen(null))));
        inner.add(new Slice(tr("life"), tr("life.hint"), DbzTheme.ICON_LIFE, -1, () -> minecraft.setScreen(new LifeScreen(null))));
        inner.add(new Slice(tr("racial"), tr("racial.hint"), DbzTheme.ICON_RACIAL, -1, () -> minecraft.setScreen(new com.dbzenith.client.screen.RacialScreen(null))));
        inner.add(new Slice(tr("settings"), tr("settings.hint"), DbzTheme.ICON_SETTINGS, -1, () -> minecraft.setScreen(new com.dbzenith.client.screen.SettingsScreen(null))));
        if (ClientPlayerData.hasData()) {
            List<String> deck = ClientPlayerData.get().deckView();
            for (int i = 0; i < deck.size(); i++) {
                Technique t = Techniques.resolve(ClientPlayerData.get(), deck.get(i));
                if (t == null) continue;
                int slot = i;
                outer.add(new Slice(t.name(), tr("technique.hint"), DbzTheme.ICON_ORB,
                        0xFF000000 | t.color(), () -> ClientCombatState.select(slot)));
            }
        }
    }

    private static Component tr(String key) {
        return Component.translatable("radial.dbzenith." + key);
    }

    private static void send(InputPacket.Action action) {
        ModNetwork.sendToServer(new InputPacket(action));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Dev automation: stays open without the key, with inner slice {@code hover} pointed at. */
    public static RadialMenuScreen dev(int hover) {
        RadialMenuScreen s = new RadialMenuScreen();
        s.devHold = true;
        s.devHover = hover;
        return s;
    }

    @Override
    public void tick() {
        if (!devHold && !InputConstants.isKeyDown(minecraft.getWindow().getWindow(), ModKeys.RADIAL.getKey().getValue())) fire();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            fire();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private void fire() {
        Slice s = hovered;
        onClose();
        if (s != null) s.action.run();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        float opened = Math.min(1f, (System.nanoTime() - openedAt) / 1.5e8f);   // 150 ms to unfold
        float ease = 1 - (1 - opened) * (1 - opened);
        float cx = width / 2f, cy = height / 2f;
        g.fillGradient(0, 0, width, height, (int) (0x70 * ease) << 24, (int) (0x90 * ease) << 24);

        double dx = mouseX - cx, dy = mouseY - cy, dist = Math.sqrt(dx * dx + dy * dy);
        double angle = (Math.toDegrees(Math.atan2(dy, dx)) + 90 + 360) % 360;   // 0 at the top, clockwise
        hovered = null;
        if (dist >= INNER_R0 * 0.6 && dist < OUTER_R0 - 3 && !inner.isEmpty()) hovered = inner.get((int) (angle / (360.0 / inner.size())) % inner.size());
        else if (dist >= OUTER_R0 - 3 && !outer.isEmpty()) hovered = outer.get((int) (angle / (360.0 / outer.size())) % outer.size());
        if (devHover >= 0 && devHover < inner.size()) hovered = inner.get(devHover);

        ring(g, inner, cx, cy, INNER_R0 * ease, INNER_R1 * ease, 22);
        if (!outer.isEmpty()) ring(g, outer, cx, cy, OUTER_R0 * ease, OUTER_R1 * ease, 16);

        // centre: what the pointed slice does
        DbzTheme.arc(g, cx, cy, 0, INNER_R0 * ease - 3, 0, 360, 0xE0182238, 0xE00A0E18);
        DbzTheme.arc(g, cx, cy, INNER_R0 * ease - 3, INNER_R0 * ease - 1, 0, 360, 0xFFD8A040, 0xFFD8A040);
        if (hovered != null) {
            Component label = hovered.label;
            float scale = Math.min(1f, (INNER_R0 * 2 - 8) / Math.max(1, font.width(label)));
            DbzTheme.text(g, font, label, cx - font.width(label) * scale / 2, cy - 12, DbzTheme.TITLE, scale);
            // the hint fits inside the centre disc, small
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(hovered.hint, 100);
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                g.pose().pushPose();
                g.pose().translate(cx - font.width(lines.get(i)) * 0.55f / 2, cy - 1 + i * 6, 0);
                g.pose().scale(0.55f, 0.55f, 1);
                g.drawString(font, lines.get(i), 0, 0, DbzTheme.TEXT, true);
                g.pose().popPose();
            }
        } else {
            Component tip = Component.translatable("radial.dbzenith.tip");
            DbzTheme.text(g, font, tip, cx - font.width(tip) * 0.6f / 2, cy - 3, DbzTheme.DIM, 0.6f);
        }
    }

    private void ring(GuiGraphics g, List<Slice> slices, float cx, float cy, float r0, float r1, int iconSize) {
        float step = 360f / slices.size();
        for (int i = 0; i < slices.size(); i++) {
            Slice s = slices.get(i);
            boolean hot = s == hovered;
            float start = -90 + i * step + 1.2f, sweep = step - 2.4f;
            float grow = hot ? 4 : 0;
            DbzTheme.arc(g, cx, cy, r0, r1 + grow, start, sweep, hot ? 0xF0402A10 : 0xD0101828, hot ? 0xF0B06018 : 0xD0223252);
            DbzTheme.arc(g, cx, cy, r1 + grow - 1.5f, r1 + grow, start, sweep, hot ? 0xFFFFE0A0 : 0xFFD8A040, hot ? 0xFFFFE0A0 : 0xFFD8A040);
            double mid = Math.toRadians(start + sweep / 2);
            float rm = (r0 + r1 + grow) / 2;
            int ix = (int) (cx + Math.cos(mid) * rm) - iconSize / 2, iy = (int) (cy + Math.sin(mid) * rm) - iconSize / 2;
            if (s.tint == -1) DbzTheme.icon(g, s.icon, ix, iy, iconSize);
            else DbzTheme.icon(g, s.icon, ix, iy, iconSize, s.tint);
        }
    }

    /** Opens on the key press; it closes itself when the key comes up. */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null) {
            mc.setScreen(new RadialMenuScreen());
            com.dbzenith.client.ClientSounds.uiOpen();
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {                                       // escape: close without acting
            hovered = null;
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
