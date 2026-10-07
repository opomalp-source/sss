package com.dbzenith.client.ui;

import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.LockOn;
import com.dbzenith.client.anim.FighterStates;
import com.dbzenith.combat.engine.Fighter;
import com.dbzenith.combat.engine.SpecialMeter;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The enemy panel (CX-19 phase 8), top right: the foe you face (locked on, else whoever you last fought) with their name,
 * form and battle power (players), health with a trail of what it just lost, and for players ki, guard and special
 * meter (from {@code FoeStatusPacket}s), plus what they are caught in (stunned, airborne, down, guarding). Client config
 * {@code enemyPanel}.
 */
public final class EnemyPanel implements IGuiOverlay {
    private static final int W = 170, BAR_H = 7;

    private static int foeId = -1;
    private static float ki = -1, guard = -1, special = -1;
    private static float shown = 1, ghost = 1, holdUntil;
    private static int shownFor = -1;

    public static void update(int entityId, float kiFrac, float guardFrac, float specialPoints) {
        foeId = entityId;
        ki = kiFrac;
        guard = guardFrac;
        special = specialPoints;
    }

    /** The foe on show: the server's word, else the locked target. */
    public static LivingEntity foe() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (foeId >= 0 && mc.level.getEntity(foeId) instanceof LivingEntity l && l.isAlive()) return l;
        return LockOn.target();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || !DBZConfig.CLIENT.enemyPanel.get()) return;
        LivingEntity foe = foe();
        if (foe == null || foe.distanceToSqr(mc.player) > 64 * 64) return;
        Font font = mc.font;
        float t = mc.level.getGameTime() + partial;
        boolean known = foeId == foe.getId();
        boolean player = foe instanceof Player;
        PublicStatePacket state = player ? ClientPublicStates.get(foe.getId()) : null;

        int x = width - W - 8, y = 6;
        int h = 30 + (known && ki >= 0 ? 10 : 0) + (known && guard >= 0 ? 6 : 0);
        DbzTheme.slant(g, x - 6, y - 3, W + 10, h, 6, 0xB0080B12, 0xC0040508);

        // name, form, power
        String name = foe.getDisplayName().getString();
        boolean locked = LockOn.target() == foe;
        DbzTheme.text(g, font, (locked ? "◎ " : "") + name, x + 2, y, locked ? 0xFFFF7A6A : DbzTheme.TEXT, 0.85f);
        if (state != null) {
            String sub = "";
            com.dbzenith.transform.Form form = com.dbzenith.transform.Forms.byId(state.form());
            if (!form.isBase()) sub = Component.translatable(form.translationKey()).getString() + "  ";
            if (state.battlePower() > 0) sub += "BP " + ComboCounter.compact(state.battlePower());
            if (!sub.isEmpty()) DbzTheme.text(g, font, sub, x + W - 4 - font.width(sub) * 0.6f, y + 1.5f, DbzTheme.TITLE, 0.6f);
        }

        // health, with the trail of what it just lost
        float hp = Mth.clamp(foe.getHealth() / Math.max(1f, foe.getMaxHealth()), 0, 1);
        if (shownFor != foe.getId()) {
            shownFor = foe.getId();
            shown = ghost = hp;
        }
        if (hp < shown) {
            if (ghost < shown) ghost = shown;
            holdUntil = t + 12;
        }
        shown = hp;
        if (t > holdUntil) ghost = Math.max(hp, ghost - 0.02f);
        int by = y + 11;
        DbzTheme.slant(g, x - 1, by - 1, W - 2, BAR_H + 2, 4, 0xF0040508, 0xF0040508);
        if (ghost > hp) DbzTheme.slantBar(g, x, by, W - 4, BAR_H, 4, ghost, 0xFFF4E8E0);
        DbzTheme.slantBar(g, x, by, W - 4, BAR_H, 4, hp, hp < 0.25f ? DbzTheme.mix(DbzTheme.BODY, 0xFFFFD0C0, 0.5f + 0.5f * Mth.sin(t * 0.6f)) : DbzTheme.BODY);
        by += BAR_H + 3;

        if (known && ki >= 0) {                                                 // ki
            DbzTheme.slant(g, x - 1, by - 1, W - 22, 6, 3, 0xF0040508, 0xF0040508);
            DbzTheme.slantBar(g, x, by, W - 24, 4, 3, ki, DbzTheme.KI);
            by += 8;
        }
        if (known && guard >= 0) {                                              // guard, and the special meter's bars
            g.fill(x, by, x + 70, by + 2, 0xA0000000);
            g.fill(x, by, x + (int) (70 * Mth.clamp(guard, 0, 1)), by + 2, guard < 0.3f ? 0xFFFF6A5A : 0xFFAEE6FF);
            if (special >= 0) {
                int bars = Math.max(3, (int) Math.ceil(special / SpecialMeter.BAR)), full = (int) (special / SpecialMeter.BAR + 1e-6);
                for (int i = 0; i < bars; i++) {
                    int sx = x + 80 + i * 14;
                    g.fill(sx, by - 1, sx + 12, by + 3, 0xB0080B12);
                    float f = i < full ? 1 : i == full ? (float) ((special - full * SpecialMeter.BAR) / SpecialMeter.BAR) : 0;
                    if (f > 0) g.fill(sx, by - 1, sx + (int) (12 * f), by + 3, i < full ? SpecialBar.FULL : SpecialBar.FILLING);
                }
            }
            by += 6;
        }

        // what they are caught in
        Fighter.State s = FighterStates.get(foe.getId());
        String chip = switch (s) {
            case STUNNED -> "hud.dbzenith.foe_stunned";
            case LAUNCHED -> "hud.dbzenith.foe_airborne";
            case KNOCKDOWN -> "hud.dbzenith.foe_down";
            case GUARDING -> "hud.dbzenith.guard";
            default -> null;
        };
        String dist = Math.round(mc.player.distanceTo(foe)) + "m";
        DbzTheme.text(g, font, dist, x + W - 6 - font.width(dist) * 0.6f, y + 11 + BAR_H + 3 + (known && ki >= 0 ? 0 : 0), DbzTheme.DIM, 0.6f);
        if (chip != null) {
            int c = s == Fighter.State.STUNNED ? 0xFFF2E94E : s == Fighter.State.LAUNCHED ? DbzTheme.ACCENT : s == Fighter.State.GUARDING ? 0xFFAEE6FF : 0xFFC0A070;
            DbzTheme.text(g, font, Component.translatable(chip), x + 2, y + h - 9, c, 0.6f);
        }
    }
}
