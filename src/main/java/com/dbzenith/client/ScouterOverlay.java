package com.dbzenith.client;

import com.dbzenith.item.ScouterItem;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scouter readout (CX-50 v2). The lens over one eye, with scanlines running down it. Every living thing in range wears
 * a small marker with its power. What you look at gets the lock-on: the reticle snaps down onto it and turns, and the
 * reading counts up digit by digit with the scouter's beeps; a reading far beyond your own flashes red.
 */
public final class ScouterOverlay implements IGuiOverlay {
    private static final int GREEN = 0xFF40FF70, DIM = 0xFF90C8A0, RED = 0xFFFF4040;
    private static final int TINT = 0x2830FF60;
    /** Ticks to lock on and to count a reading up. */
    private static final float LOCK = 7, COUNT = 16;

    private static int lockedId = -1;
    private static float lockedAt;
    private static int lastBeep;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || com.dbzenith.client.space.JourneyClient.active() || !ScouterItem.wears(mc.player)) {
            lockedId = -1;
            return;
        }
        float now = mc.level.getGameTime() + partialTick;
        lens(g, width, height, now);
        com.dbzenith.client.space.PlanetExperience.scouter(g,width,height,partialTick);

        Font font = mc.font;
        long mine = powerOf(mc.player);
        LivingEntity target = ScouterItem.target(mc.player);

        // markers over everything in range
        List<LivingEntity> near = new ArrayList<>();
        double range = ScouterItem.range();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity l && l != mc.player && l.isAlive() && !l.isInvisible() && l.distanceToSqr(mc.player) < range * range) near.add(l);
        }
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        for (int i = 0; i < Math.min(12, near.size()); i++) {
            LivingEntity e = near.get(i);
            if (e == target) continue;
            Vec3 s = KiSenseHud.project(mc, e.getPosition(partialTick).add(0, e.getBbHeight() + 0.35, 0), width, height);
            if (s == null || s.x < 0 || s.y < 0 || s.x > width || s.y > height) continue;
            int x = (int) s.x, y = (int) s.y;
            g.fill(x - 2, y, x + 3, y + 1, GREEN);
            g.fill(x, y - 2, x + 1, y + 3, GREEN);
            long p = powerOf(e);
            String text = p < 0 ? "???" : KiSenseHud.compact(p);
            g.drawString(font, text, x - font.width(text) / 2, y - 11, p > mine * 5 && mine > 0 ? RED : DIM);
        }

        if (target == null) {
            lockedId = -1;
            return;
        }
        if (target.getId() != lockedId) {                                     // a new lock
            lockedId = target.getId();
            lockedAt = now;
        }
        float since = now - lockedAt;
        float lock = Math.min(1, since / LOCK);
        float ease = 1 - (1 - lock) * (1 - lock) * (1 - lock);
        long power = powerOf(target);
        float counted = Math.min(1, Math.max(0, (since - LOCK * 0.5f) / COUNT));
        if (counted < 1 && (int) since != lastBeep && (int) since % 2 == 0) {  // the beeps as it reads
            lastBeep = (int) since;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_CLICK.get(), 1.6f + counted * 0.6f, 0.35f));
        }

        // the reticle: snaps down from wide onto the target, turning as it closes
        Vec3 at = KiSenseHud.project(mc, target.getPosition(partialTick).add(0, target.getBbHeight() * 0.55, 0), width, height);
        int cx = at == null ? width / 2 : (int) at.x, cy = at == null ? height / 2 : (int) at.y;
        float r = Mth.lerp(ease, 44, 14);
        float turn = (1 - ease) * 1.4f;
        bracket(g, cx, cy, r, turn, GREEN);
        if (lock >= 1 && ((int) (now / 4) & 1) == 0) g.fill(cx - 1, cy - 1, cx + 1, cy + 1, GREEN);

        // the reading, counting up
        boolean danger = power > mine * 5 && mine > 0;
        int col = danger && ((int) (now / 3) & 1) == 0 ? RED : GREEN;
        Component name = target.getDisplayName();
        Component reading;
        if (power < 0) reading = Component.translatable("hud.dbzenith.scouter_unreadable");
        else {
            long shown = counted >= 1 ? power : (long) (power * counted * counted) + (long) (Math.random() * Math.max(1, power * 0.02 * (1 - counted)));
            reading = Component.translatable("hud.dbzenith.scouter_power", String.format("%,d", Math.min(power, shown)));
        }
        Component dist = Component.translatable("hud.dbzenith.scouter_distance", (int) mc.player.distanceTo(target));
        int tx = (int) (cx + r + 6), ty = (int) (cy - r);
        g.drawString(font, name, tx, ty, GREEN);
        g.drawString(font, reading, tx, ty + 10, col);
        g.drawString(font, dist, tx, ty + 20, DIM);
        if (danger && counted >= 1) g.drawString(font, "!!", tx, ty + 30, col);
    }

    /** The lens over the right eye: tinted, edged, scanlines running down it, a blinking SCAN. */
    private static void lens(GuiGraphics g, int width, int height, float now) {
        int x0 = width - Math.max(120, width / 5);
        g.fill(x0, 0, width, height, TINT);
        g.fill(x0, 0, x0 + 1, height, 0x6040FF70);
        for (int y = (int) (now * 1.5f) % 4; y < height; y += 4) g.fill(x0, y, width, y + 1, 0x0C40FF70);
        int sweep = (int) (now * 6) % (height + 40) - 20;                        // a brighter line sweeping down
        g.fill(x0, sweep, width, sweep + 2, 0x2040FF70);
        if (((int) (now / 10) & 1) == 0) g.drawString(Minecraft.getInstance().font, "SCAN", width - 30, 6, 0xA040FF70);
    }

    /** Four corner brackets round (cx, cy), {@code r} out, turned by {@code turn} radians. */
    private static void bracket(GuiGraphics g, int cx, int cy, float r, float turn, int color) {
        for (int k = 0; k < 4; k++) {
            float a = turn + k * Mth.HALF_PI + Mth.PI / 4;
            int x = cx + Math.round(Mth.cos(a) * r * 1.414f), y = cy + Math.round(Mth.sin(a) * r * 1.414f);
            int sx = Mth.cos(a) > 0 ? -1 : 1, sy = Mth.sin(a) > 0 ? -1 : 1;
            g.fill(Math.min(x, x + sx * 5), y, Math.max(x, x + sx * 5) + 1, y + 1, color);
            g.fill(x, Math.min(y, y + sy * 5), x + 1, Math.max(y, y + sy * 5) + 1, color);
        }
    }

    /** Other players' power comes from their synced public state; creatures are estimated client-side. */
    private static long powerOf(LivingEntity e) {
        if (e instanceof Player p) {
            PublicStatePacket state = ClientPublicStates.get(p.getId());
            return state == null ? 0 : state.battlePower();
        }
        return ScouterItem.mobPower(e);
    }
}
