package com.dbzenith.client;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Ki Sense on the HUD. Level 1: a warning when a ki far stronger than yours is near, with its direction. Level 2:
 * arrows around the crosshair for ki attacks flying at you. Level 3: the scan (the Skill key) marks every ki around
 * you for ten seconds, with its power. God ki cannot be felt without god ki.
 */
public final class KiSenseHud {
    private KiSenseHud() {}

    public static void render(GuiGraphics g, Font font, PlayerData d, int width, int height, float partial, float t) {
        int level = d.getSkillLevel("ki_sense");
        Minecraft mc = Minecraft.getInstance();
        if (level <= 0 || mc.player == null || mc.level == null) return;
        Player me = mc.player;
        long mine = StatCalculator.battlePower(d);
        boolean divine = d.hasFlag("god_ki");
        float cx = width / 2f, cy = height / 2f;

        // level 1: the strongest ki around, if it dwarfs yours
        Player strongest = null;
        long best = 0;
        for (Player p : mc.level.players()) {
            if (p == me || p.distanceTo(me) > 64) continue;
            PublicStatePacket s = ClientPublicStates.get(p.getId());
            if (s == null || s.battlePower() < 0 && !divine) continue;              // god ki slips past the senses
            long bp = s.battlePower() < 0 ? Long.MAX_VALUE / 4 : s.battlePower();
            if (bp > mine * 1.5 && bp > best) {
                best = bp;
                strongest = p;
            }
        }
        if (strongest != null) {
            float pulse = 0.6f + 0.4f * Mth.sin(t * 0.35f);
            int red = DbzTheme.withAlpha(0xFF5040, (int) (255 * pulse));
            Component warn = Component.translatable("hud.dbzenith.ki_sense_warning", (int) strongest.distanceTo(me));
            DbzTheme.text(g, font, warn, cx - font.width(warn) * 0.8f / 2, 34, red, 0.8f);
            arrow(g, cx, 52, angleTo(me, strongest.position(), partial), 9, red);
        }

        // level 2: ki attacks on their way to you
        if (level >= 2) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof KiBlastEntity blast) || blast.getOwner() == me) continue;
                Vec3 to = me.position().add(0, 1, 0).subtract(blast.position());
                double dist = to.length();
                Vec3 v = blast.getDeltaMovement();
                double speed = v.length();
                if (dist > 48 || speed < 0.05 || v.dot(to) < 0.85 * speed * dist || dist / speed > 60) continue;
                float urgency = (float) Mth.clamp(1 - dist / speed / 60, 0.25, 1);
                int c = DbzTheme.withAlpha(blast.getColor() & 0xFFFFFF, (int) (255 * urgency));
                float a = angleTo(me, blast.position(), partial);
                arrow(g, cx + Mth.sin(a) * 34, cy - Mth.cos(a) * 34, a, 5 + 4 * urgency, c);
            }
        }

        // level 3: the scan
        Long began = d.getRacialBuffs().get("ki_sense");
        if (level >= 3 && began != null && mc.level.getGameTime() - began < 200) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity le) || e == me || e.distanceTo(me) > 64 || !le.isAlive()) continue;
                long bp;
                if (e instanceof Player p) {
                    PublicStatePacket s = ClientPublicStates.get(p.getId());
                    if (s == null || s.battlePower() < 0 && !divine) continue;
                    bp = s.battlePower();
                } else {
                    bp = Math.round(com.dbzenith.npc.KiFighter.effectiveMaxHealth(le) * 25);
                }
                Vec3 sp = project(mc, e.getPosition(partial).add(0, e.getBbHeight() + 0.4, 0), width, height);
                if (sp == null) continue;
                int c = bp > mine * 1.5 ? 0xFFFF5040 : bp > mine * 0.67 ? 0xFFFFD040 : 0xFF70E070;
                String label = bp < 0 ? "???" : compact(bp);
                g.fill((int) sp.x - 2, (int) sp.y - 2, (int) sp.x + 2, (int) sp.y + 2, c);
                DbzTheme.text(g, font, label, (float) sp.x - font.width(label) * 0.7f / 2, (float) sp.y - 10, c, 0.7f);
            }
        }
    }

    /** The screen angle (0 = straight ahead, clockwise) from the camera to a point. */
    static float angleTo(Player me, Vec3 target, float partial) {
        Vec3 rel = target.subtract(me.getPosition(partial));
        float yaw = me.getViewYRot(partial) * Mth.DEG_TO_RAD;
        double fwd = -rel.x * Mth.sin(yaw) + rel.z * Mth.cos(yaw), right = -rel.x * Mth.cos(yaw) - rel.z * Mth.sin(yaw);
        return (float) Math.atan2(right, fwd);
    }

    /** A filled triangle pointing at {@code angle}. */
    static void arrow(GuiGraphics g, float x, float y, float angle, float size, int color) {
        float s = Mth.sin(angle), c = Mth.cos(angle);
        float tx = x + s * size, ty = y - c * size;
        float lx = x - c * size * 0.6f - s * size * 0.5f, ly = y - s * size * 0.6f + c * size * 0.5f;
        float rx = x + c * size * 0.6f - s * size * 0.5f, ry = y + s * size * 0.6f + c * size * 0.5f;
        DbzTheme.quad(g, tx, ty, rx, ry, x - s * size * 0.2f, y + c * size * 0.2f, lx, ly, color, color, color, color);
    }

    /** World to screen, or null when behind the camera. */
    static Vec3 project(Minecraft mc, Vec3 world, int width, int height) {
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 rel = world.subtract(camera.getPosition());
        org.joml.Vector3f v = new org.joml.Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        org.joml.Quaternionf q = new org.joml.Quaternionf(camera.rotation()).conjugate();
        q.transform(v);
        if (v.z <= 0.05f) return null;                        // the camera looks down +z, +x to its left
        double fov = mc.options.fov().get() * Mth.DEG_TO_RAD;
        double f = (height / 2.0) / Math.tan(fov / 2);
        return new Vec3(width / 2.0 - v.x / v.z * f, height / 2.0 - v.y / v.z * f, 0);
    }

    static String compact(long v) {
        if (v >= 1_000_000_000L) return String.format("%.1fB", v / 1e9);
        if (v >= 1_000_000L) return String.format("%.1fM", v / 1e6);
        if (v >= 10_000L) return String.format("%.0fk", v / 1e3);
        return String.valueOf(v);
    }
}
