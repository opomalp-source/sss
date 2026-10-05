package com.dbzenith.client;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.PortraitRenderer;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.skill.Technique;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormMath;
import com.dbzenith.transform.Forms;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The portrait HUD. Top left: your live head in a ring tinted by your form, a release gauge arcing around it, and
 * slanted body / ki / stamina bars that ease up, drop at once and leave a pale "ghost" of what was just lost. Under
 * them the battle power, the form badge and status chips. A combo counter pops beside the crosshair, and the selected
 * technique sits right of the hotbar with its cooldown sweeping round its orb.
 */
public final class DbzHud implements IGuiOverlay {
    private static final ResourceLocation CHIPS = new ResourceLocation(com.dbzenith.DBZenith.MOD_ID, "textures/gui/hud.png");
    private static final int GOLD = 0xFFD8A040;

    // eased bar state: body, ki, stamina
    private static final float[] SHOWN = new float[3];
    private static final float[] GHOST = new float[3];
    private static final float[] HOLD_UNTIL = new float[3];
    private static float lastT = -1;
    private static int lastCombo;
    private static float comboAt;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.options.renderDebug || mc.player == null || !ClientPlayerData.hasData()) return;
        PlayerData d = ClientPlayerData.get();
        DerivedStats s = d.getDerived();
        Font font = mc.font;
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        float t = time + partialTick;
        float dt = lastT < 0 ? 0 : Mth.clamp(t - lastT, 0, 5);
        lastT = t;

        PublicStatePacket pub = ClientPublicStates.get(mc.player.getId());
        Form form = Forms.byId(d.getFormId());
        boolean held = !form.isBase() || d.getOverdriveLevel() > 0;
        int aura = 0xFF000000 | (pub != null ? pub.auraColor() : com.dbzenith.ki.Aura.DEFAULT_COLOR);
        int accent = held ? aura : GOLD;
        RenderSystem.enableBlend();
        float hudScale = (float) (double) com.dbzenith.config.DBZConfig.CLIENT.hudScale.get();
        g.pose().pushPose();
        g.pose().scale(hudScale, hudScale, 1);

        // ---------------------------------------------------------- portrait
        int px = 6, py = 4, cx = px + 28, cy = py + 28;
        g.blit(DbzTheme.UI, px, py, 56, 0, 56, 56, 256, 256);                            // backplate
        if (held || d.isCharging()) {
            float pulse = 0.75f + 0.25f * Mth.sin(t * (d.isCharging() ? 0.8f : 0.25f));
            DbzTheme.arc(g, cx, cy, 0, 22, 0, 360, DbzTheme.withAlpha(aura, 0), DbzTheme.withAlpha(aura, (int) (170 * pulse)));
        }
        int c0 = (int) ((cx - 19) * hudScale), c1 = (int) ((cy - 19) * hudScale), c2 = (int) ((cx + 19) * hudScale), c3 = (int) ((cy + 19) * hudScale);
        PortraitRenderer.draw(g, mc.player, cx, cy + 5, 44f, 14f, new int[]{c0, c1, c2, c3});   // the clip is in screen space
        g.setColor(((accent >> 16) & 255) / 255f, ((accent >> 8) & 255) / 255f, (accent & 255) / 255f, 1f);
        g.blit(DbzTheme.UI, px, py, 0, 0, 56, 56, 256, 256);                              // ring, tinted
        g.setColor(1, 1, 1, 1);
        // release gauge around the ring; past 100% it laps again in red
        float release = d.getReleasePercent() / 100f;
        DbzTheme.arc(g, cx, cy, 28.5f, 31f, -90, 360, 0xC0101018, 0xC0101018);
        DbzTheme.arc(g, cx, cy, 28.5f, 31f, -90, 360 * Math.min(1f, release), 0xFFB0500E, DbzTheme.ACCENT);
        if (release > 1f) DbzTheme.arc(g, cx, cy, 28.5f, 31f, -90, 360 * Math.min(1f, release - 1f), 0xFF901010, 0xFFFF4030);
        DbzTheme.text(g, font, d.getReleasePercent() + "%", cx - font.width(d.getReleasePercent() + "%") * 0.6f / 2, py + 52, DbzTheme.ACCENT, 0.6f);

        // ---------------------------------------------------------- bars
        int bx = px + 54, by = py + 5;
        int bodyColor = DbzTheme.BODY;
        float bodyFrac = frac(d.getBody(), s.maxBody());
        if (bodyFrac < 0.25f) bodyColor = DbzTheme.mix(DbzTheme.BODY, 0xFFFFD0C0, 0.5f + 0.5f * Mth.sin(t * 0.6f));
        int kiColor = held ? DbzTheme.mix(DbzTheme.KI, aura, 0.25f) : DbzTheme.KI;
        by = bar(g, font, 0, bx, by, 112, 8, bodyFrac, d.getBody(), bodyColor, t, dt);
        by = bar(g, font, 1, bx - 2, by, 102, 7, frac(d.getKi(), s.maxKi()), d.getKi(), kiColor, t, dt);
        by = bar(g, font, 2, bx - 4, by, 92, 6, frac(d.getStamina(), s.maxStamina()), d.getStamina(), DbzTheme.STAMINA, t, dt);
        if (d.isGuarding() || d.getGuardMeter() < 100) {                                 // guard meter, only when it matters
            float gf = (float) (d.getGuardMeter() / 100);
            int gc = gf < 0.3f ? DbzTheme.mix(0xFFAEE6FF, 0xFFFF6A5A, 0.5f + 0.5f * Mth.sin(t * 0.8f)) : 0xFFAEE6FF;
            DbzTheme.slant(g, bx - 7, by - 1, 82, 5, 3, 0xF0040508, 0xF0040508);
            DbzTheme.slantBar(g, bx - 6, by, 80, 3, 2, gf, gc);
            for (int i = 0; i < 2; i++) {                                                  // Breaker Wave charges
                int pc = i < d.combat().breakerCharges ? 0xFFFFD27A : 0x60FFFFFF;
                DbzTheme.quad(g, bx + 80 + i * 6, by - 1, bx + 83 + i * 6, by + 1.5f, bx + 80 + i * 6, by + 4, bx + 77 + i * 6, by + 1.5f, pc, pc, pc, pc);
            }
            by += 6;
        }

        // ---------------------------------------------------------- battle power, form badge, overdrive
        long bp = StatCalculator.battlePower(d);
        int ly = by + 1;
        DbzTheme.text(g, font, Component.translatable("hud.dbzenith.bp"), bx + 5, ly + 2, DbzTheme.DIM, 0.7f);
        g.drawString(font, String.format("%,d", bp), bx + 18, ly, held ? DbzTheme.brighten(aura, 1.1f) : DbzTheme.TITLE, true);
        ly += 11;
        if (!form.isBase()) {
            Component name = Component.translatable(form.translationKey());
            int nw = font.width(name);
            DbzTheme.slant(g, bx - 6, ly, nw + 44, 11, 4, DbzTheme.withAlpha(DbzTheme.darken(aura, 0.75f), 230), DbzTheme.withAlpha(DbzTheme.darken(aura, 0.35f), 230));
            g.drawString(font, name, bx, ly + 2, 0xFFFFFFFF, true);
            DbzTheme.text(g, font, String.format("M%.0f%%", d.getMastery(form.id())), bx + nw + 6, ly + 3, 0xE0FFFFFF, 0.7f);
            ly += 13;
        }
        int chipX = bx - 6;
        if (d.getOverdriveLevel() > 0) {
            chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.overdrive", String.format("%.0f", FormMath.overdriveMultiplier(d))), 0xFFFF4030);
        }

        // ---------------------------------------------------------- status chips
        if (d.isCharging()) {
            int pulse = 0xFF000000 | (int) (180 + 75 * Mth.sin(t * 0.6f)) << 8 | 0xFF;
            chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.charging"), pulse);
        }
        if (d.combat().downedFlag) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.downed"), 0xFFC0A070);
        if (time < d.combat().chaseReadyUntil) {
            int pulse = DbzTheme.mix(0xFFFFFFFF, 0xFF7CE0FF, 0.5f + 0.5f * Mth.sin(t * 1.2f));
            chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.chase"), pulse);
        }
        if (d.getKaiokenStage() > 0) {
            int pulse = DbzTheme.mix(0xFFFF2A1E, 0xFFFFC0A0, 0.5f + 0.5f * Mth.sin(t * 0.5f));
            chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.kaioken", d.getKaiokenStage()), pulse);
        }
        for (String buff : new String[]{"limit_break", "ki_barrier", "desperate_gambit"}) {          // universal buffs and their backlash
            com.dbzenith.race.RacialSkill rs = com.dbzenith.race.RacialSkills.byId(buff);
            if (d.getRacialActive().contains(buff)) chipX = chip(g, font, chipX, ly, Component.translatable(rs.translationKey()), rs.color());
            else if (d.getRacialAfter().contains(buff)) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.backlash",
                    Component.translatable(rs.translationKey())), 0xFF909098);
        }
        if (d.isFlying()) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.flying"), DbzTheme.KI);
        if (com.dbzenith.registry.ModEffects.isStunned(mc.player)) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.stunned"), 0xFFF2E94E);
        if (com.dbzenith.registry.ModEffects.isKiSealed(mc.player)) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.sealed"), 0xFF8A5FD0);
        if (d.isGuarding()) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.guard"), DbzTheme.STAMINA);
        if (time < d.getGuardLockUntil()) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.guard_broken"), 0xFFFF6A5A);
        if (com.dbzenith.config.DBZConfig.SERVER_SPEC.isLoaded() && com.dbzenith.config.DBZConfig.SERVER.thirstEnabled.get()
                && d.getThirst() < com.dbzenith.world.Needs.THIRSTY_BELOW) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.thirsty"), 0xFF60B0FF);
        if (d.getTemperature() > 0) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.hot"), 0xFFFF8030);
        if (d.getTemperature() < 0) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.cold"), 0xFFA0E0FF);
        if (d.getMajinStacks() > 0) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.absorbed", d.getMajinStacks()), 0xFFFF80C0);
        if (d.isMeditating()) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.meditating"), 0xFFC8A0FF);
        if (d.getGravity() > 1) chipX = chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.gravity", (int) d.getGravity()), 0xFFB070FF);
        if (d.isChargingHeavy()) chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.heavy_charging"), 0xFFFF6040);
        else if (d.getHeavyArmedMultiplier() > 0) {
            chip(g, font, chipX, ly, Component.translatable("hud.dbzenith.heavy_ready", String.format("%.1f", d.getHeavyArmedMultiplier())), 0xFFFF6040);
        }

        g.pose().popPose();

        // ---------------------------------------------------------- combo
        int combo = d.getComboHits();
        if (combo != lastCombo) {
            if (combo > lastCombo) comboAt = t;
            lastCombo = combo;
        }
        if (combo >= 2) {
            float pop = Math.max(0, 1 - (t - comboAt) / 5f);
            float scale = 2.2f + 0.9f * pop * pop;
            String n = String.valueOf(combo);
            int color = combo >= 10 ? 0xFFFF5030 : combo >= 5 ? DbzTheme.ACCENT : DbzTheme.TITLE;
            g.pose().pushPose();
            g.pose().translate(width / 2f + 22, height / 2f - 30, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, n, -font.width(n) / 2, -4, color, true);
            g.pose().popPose();
            DbzTheme.text(g, font, Component.translatable("hud.dbzenith.hits"), width / 2f + 22 + font.width(n) * scale / 2 + 2, height / 2f - 30, DbzTheme.TEXT, 0.9f);
        }

        // ---------------------------------------------------------- powering up into a form
        if (d.isTransforming()) {
            Form target = Forms.byId(d.getTransformTarget());
            int tc = 0xFF000000 | target.auraColor();
            float p = Mth.clamp(d.getTransformTicks() / (float) Math.max(1, d.getTransformTotal()), 0, 1);
            int w = 120, h = 6, x = width / 2 - w / 2, y = height / 2 + 26;
            float shake = p > 0.7f ? (p - 0.7f) * 4 * Mth.sin(t * 3.1f) : 0;
            Component label = Component.translatable("hud.dbzenith.powering_up", Component.translatable(target.translationKey()));
            DbzTheme.text(g, font, label, width / 2f - font.width(label) * 0.8f / 2 + shake, y - 9, tc, 0.8f);
            DbzTheme.slant(g, x - 1 + shake, y - 1, w + 2, h + 2, 3, 0xF0040508, 0xF0040508);
            DbzTheme.slantBar(g, x + shake, y, w, h, 3, p, DbzTheme.mix(tc, 0xFFFFFFFF, 0.25f + 0.25f * Mth.sin(t * 0.9f)));
            DbzTheme.text(g, font, Component.translatable("hud.dbzenith.powering_hint"), width / 2f - font.width(Component.translatable("hud.dbzenith.powering_hint")) * 0.6f / 2,
                    y + h + 3, DbzTheme.DIM, 0.6f);
        }

        // ---------------------------------------------------------- technique, right of the hotbar
        technique(g, font, d, width / 2 + 114, height - 24, time, t);
        racial(g, font, d, width / 2 - 114 - 86, height - 24, time, t);
        skill(g, font, d, width / 2 - 114 - 86, height - 50, time, t);
        KiSenseHud.render(g, font, d, width, height, partialTick, t);
    }

    /** One slanted bar with its eased fill, ghost of recent loss, shimmer and value. Returns the next row's y. */
    private static int bar(GuiGraphics g, Font font, int i, int x, int y, int w, int h, float frac, double value, int color, float t, float dt) {
        float skew = h * 0.6f;
        if (frac < SHOWN[i]) {                                 // loss: drop at once, hold the ghost, then drain it
            if (GHOST[i] < SHOWN[i]) GHOST[i] = SHOWN[i];
            SHOWN[i] = frac;
            HOLD_UNTIL[i] = t + 12;
        } else {
            SHOWN[i] += (frac - SHOWN[i]) * Math.min(1f, dt * 0.35f);
        }
        if (t > HOLD_UNTIL[i]) GHOST[i] = Math.max(SHOWN[i], GHOST[i] - dt * 0.025f);
        if (GHOST[i] < SHOWN[i]) GHOST[i] = SHOWN[i];

        DbzTheme.slant(g, x - 1, y - 1, w + 2, h + 2, skew, 0xF0040508, 0xF0040508);
        DbzTheme.slant(g, x, y, w, h, skew, 0xE0161C2C, 0xE00A0E18);
        if (GHOST[i] > SHOWN[i] + 0.002f) DbzTheme.slantBar(g, x, y, w, h, skew, GHOST[i], 0xFFF4E8E0);
        DbzTheme.slantBar(g, x, y, w, h, skew, SHOWN[i], color);
        // a highlight sweeping along the fill every few seconds
        float sweep = ((t * 2.2f + i * 40) % 260) - 30;
        float fillW = w * SHOWN[i];
        if (sweep > 0 && sweep < fillW - 6) {
            float sx = x + sweep;
            DbzTheme.quad(g, sx + skew, y, sx + skew + 6, y, sx + 6, y + h, sx, y + h, 0x60FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x60FFFFFF);
        }
        for (int k = 1; k < 4; k++) {                          // quarter marks
            float mx = x + w * k / 4f;
            DbzTheme.slant(g, mx, y + 1, 1, h - 2, skew * (h - 2) / h, 0x50000000, 0x50000000);
        }
        DbzTheme.text(g, font, compact(value), x + w + skew + 3, y + (h - 6) / 2f, DbzTheme.TEXT, 0.75f);
        return y + h + 3;
    }

    /** The universal skill on the Skill key, above the racial one. */
    private static void skill(GuiGraphics g, Font font, PlayerData d, int x, int y, long time, float t) {
        com.dbzenith.race.RacialSkill s = com.dbzenith.race.RacialSkills.byId(d.getSkillSelected());
        if (s == null || !com.dbzenith.race.RacialSkills.unlocked(d, s)) return;
        skillChip(g, font, d, s, x, y, time, t, "hud.dbzenith.skill_ready");
    }

    /** The active racial skill on the Racial key, left of the hotbar: ready, cooling down, or running. */
    private static void racial(GuiGraphics g, Font font, PlayerData d, int x, int y, long time, float t) {
        com.dbzenith.race.RacialSkill s = com.dbzenith.race.RacialSkills.byId(d.getRacialSelected());
        if (s == null || !com.dbzenith.race.RacialSkills.unlocked(d, s)) {
            s = com.dbzenith.race.RacialSkills.forCharacter(d.getRace(), d.getVariant()).stream()
                    .filter(r -> r.isActive() && com.dbzenith.race.RacialSkills.unlocked(d, r)).findFirst().orElse(null);
        }
        if (s == null) return;
        skillChip(g, font, d, s, x, y, time, t, "hud.dbzenith.racial_ready");
    }

    private static void skillChip(GuiGraphics g, Font font, PlayerData d, com.dbzenith.race.RacialSkill s, int x, int y, long time, float t, String readyKey) {
        int w = 86, h = 22, color = s.color();
        DbzTheme.slant(g, x - 1, y - 1, w + 2, h + 2, 6, 0xF0040508, 0xF0040508);
        DbzTheme.slant(g, x, y, w, h, 6, 0xE0182238, 0xE00A0E18);
        long ready = d.getRacialCooldown(s.id());
        float cd = time < ready ? (ready - time) / (float) Math.max(1, s.cooldownTicks()) : 0;
        Long began = d.getRacialBuffs().get(s.id());
        boolean running = began != null && time - began < s.durationTicks();
        int ox = x + 5, oy = y + 3;
        DbzTheme.icon(g, DbzTheme.ICON_RACIAL, ox, oy, 16, cd > 0 && !running ? DbzTheme.darken(color, 0.5f) : color);
        if (running) DbzTheme.arc(g, ox + 8, oy + 8, 8.5f, 10.5f, -90, 360 * (1 - (time - began) / (float) s.durationTicks()), color, color);
        else if (cd > 0) DbzTheme.arc(g, ox + 8, oy + 8, 0, 9, -90, -360 * cd, 0x90000000, 0x90000000);
        Component name = Component.translatable(s.translationKey());
        DbzTheme.text(g, font, name, x + 25, y + 3, color, font.width(name) > 58 ? 58f / font.width(name) : 1f);
        Component state = running ? Component.translatable("hud.dbzenith.racial_running", (s.durationTicks() - (time - began)) / 20 + 1)
                : cd > 0 ? Component.translatable("hud.dbzenith.racial_cooldown", (ready - time) / 20 + 1)
                : Component.translatable(readyKey);
        DbzTheme.text(g, font, state, x + 25, y + 13, running ? color : cd > 0 ? DbzTheme.DIM : DbzTheme.GOOD, 0.6f);
    }

    private static void technique(GuiGraphics g, Font font, PlayerData d, int x, int y, long time, float t) {
        Technique tech = ClientCombatState.selected();
        int w = 94, h = 22;
        DbzTheme.slant(g, x - 1, y - 1, w + 2, h + 2, 6, 0xF0040508, 0xF0040508);
        DbzTheme.slant(g, x, y, w, h, 6, 0xE0182238, 0xE00A0E18);
        if (tech == null) {
            DbzTheme.text(g, font, Component.translatable("hud.dbzenith.empty_deck"), x + 6, y + 8, DbzTheme.DIM, 0.6f);
            return;
        }
        int color = 0xFF000000 | tech.color();
        float cd = ClientCombatState.cooldownFraction(tech, time);
        int ox = x + 5, oy = y + 3;
        DbzTheme.icon(g, DbzTheme.ICON_ORB, ox, oy, 16, cd > 0 ? DbzTheme.darken(color, 0.5f) : color);
        if (cd > 0) DbzTheme.arc(g, ox + 8, oy + 8, 0, 9, -90, -360 * cd, 0x90000000, 0x90000000);
        else DbzTheme.arc(g, ox + 8, oy + 8, 8.5f, 10f, 0, 360, DbzTheme.withAlpha(color, 0), DbzTheme.withAlpha(color, (int) (120 + 60 * Mth.sin(t * 0.3f))));
        Component name = tech.name();
        DbzTheme.text(g, font, name, x + 25, y + 3, color, font.width(name) > 70 ? 70f / font.width(name) : 1f);
        DbzTheme.text(g, font, Component.translatable("hud.dbzenith.technique_keys", ClientCombatState.selectedSlot() + 1, d.deckView().size()),
                x + 25, y + 13, DbzTheme.DIM, 0.6f);
    }

    private static int chip(GuiGraphics g, Font font, int x, int y, Component text, int color) {
        int w = (int) (font.width(text) * 0.75f) + 6;
        g.blitNineSliced(CHIPS, x, y - 1, w, 9, 3, 3, 16, 16, 16, 0);
        DbzTheme.text(g, font, text, x + 3, y + 1, color, 0.75f);
        return x + w + 2;
    }

    private static float frac(double v, double max) {
        return max <= 0 ? 0 : (float) Mth.clamp(v / max, 0, 1);
    }

    private static String compact(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 10_000) return String.format("%.1fk", v / 1_000);
        return String.valueOf((int) Math.round(v));
    }
}
