package com.dbzenith.client;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.Ui;
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
    private static int chipLeft = 54;

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
        boolean held = !form.isBase() || d.getKaiokenStage() > 0;
        int aura = 0xFF000000 | (pub != null ? pub.auraColor() : com.dbzenith.ki.Aura.DEFAULT_COLOR);
        int accent = held ? aura : GOLD;
        RenderSystem.enableBlend();
        float hudScale = (float) (double) com.dbzenith.config.DBZConfig.CLIENT.hudScale.get();
        g.pose().pushPose();
        g.pose().scale(hudScale, hudScale, 1);

        int style = hudStyle();
        int ly = switch (style) {
            case 1 -> classic(g, font, mc, d, s, form, held, aura, accent, t, dt, hudScale);
            case 2 -> minimal(g, font, d, s, form, held, aura, t, dt);
            case 3 -> zenith(g, font, mc, d, s, form, held, aura, accent, t, dt, hudScale);
            case 4 -> clean(g, font, mc, d, s, form, held, aura, t, dt, hudScale);
            default -> {                                                                 // the Saga HUD (CX-16e)
                int[] at = SagaHud.render(g, font, mc, d, s, form, held, aura, t, dt, hudScale);
                chipLeft = at[0];
                yield at[1];
            }
        };
        int chipX = chipLeft;

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
        ease(i, frac, t, dt);

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
        chipBack(g, x, y, w, h);
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
        chipBack(g, x, y, w, h);
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


    /** The original HUD: a tinted ring portrait and slanted bars. Returns the y of the status chip row. */
    private static int classic(GuiGraphics g, Font font, Minecraft mc, PlayerData d, DerivedStats s, Form form, boolean held, int aura, int accent,
                               float t, float dt, float hudScale) {
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

        // ---------------------------------------------------------- battle power and the form badge
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
        chipLeft = bx - 6;
        return ly;
    }

    // ================================================================== Zenith (CX-13e)

    private static final ResourceLocation FRAMES = new ResourceLocation(com.dbzenith.DBZenith.MOD_ID, "textures/gui/hud_frames.png");
    private static final ResourceLocation BARS = new ResourceLocation(com.dbzenith.DBZenith.MOD_ID, "textures/gui/hud_bars.png");
    private static final ResourceLocation ENERGY = new ResourceLocation(com.dbzenith.DBZenith.MOD_ID, "textures/gui/hud_energy.png");
    public static final int FRAME_STANDARD = 0, FRAME_FLAME = 1, FRAME_DIVINE = 2, FRAME_SAVAGE = 3, FRAME_REGAL = 4, FRAME_TECH = 5;
    private static final String[] SAVAGE = {"ape", "lssj", "super_saiyan_4", "ssj4", "berserk", "wrath", "rage", "evil", "demon", "blood", "corruption",
            "mutant", "mutation", "beast", "primordial", "thirst", "nightborn", "monarch", "crimson", "revenge", "dark_evolution", "apex"};
    private static final String[] TECH = {"machine", "metal", "overclock", "upgrade", "omega", "core", "android", "protocol", "neural", "conversion", "tuffle"};
    private static final String[] REGAL = {"form", "perfect", "king", "sovereign", "warlord", "dragon", "elder", "ultimate"};

    /** 0 Saga (CX-16e, the default), 1 Classic, 2 Minimal, 3 Ornate (the old Zenith frame), 4 Clean. */
    public static int hudStyle() {
        try {
            return com.dbzenith.config.DBZConfig.CLIENT.hudStyle.get();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Which portrait frame a form wears: god ki gets the divine halo, wild forms the thorns, machines the brackets... */
    public static int frameStyle(Form f, boolean kaioken) {
        if (f.isBase()) return kaioken ? FRAME_FLAME : FRAME_STANDARD;
        if (f.calmAura()) return FRAME_DIVINE;
        String id = f.id();
        for (String k : SAVAGE) if (id.contains(k)) return FRAME_SAVAGE;
        for (String k : TECH) if (id.contains(k)) return FRAME_TECH;
        for (String k : REGAL) if (id.contains(k)) return FRAME_REGAL;
        return FRAME_FLAME;
    }

    /** Blits a region of a quarter-scale sheet at true size. */
    private static void hd(GuiGraphics g, ResourceLocation tex, float x, float y, int u, int v, int w, int h, int texW, int texH) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.25f, 0.25f, 1);
        g.blit(tex, 0, 0, u, v, w, h, texW, texH);
        g.pose().popPose();
    }

    private static void tint(GuiGraphics g, int argb, float alpha) {
        g.setColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, alpha);
    }

    /**
     * An ornate frame that changes with the form round your live portrait, the release gauge set into its track, and
     * energy bars plugged into it. Returns the y of the status chip row.
     */
    private static int zenith(GuiGraphics g, Font font, Minecraft mc, PlayerData d, DerivedStats s, Form form, boolean held, int aura, int accent,
                              float t, float dt, float hudScale) {
        int px = 4, py = 4, cx = px + 32, cy = py + 32;
        int frame = frameStyle(form, d.getKaiokenStage() > 0);

        if (held || d.isCharging()) {                                                     // the aura flaring behind the frame
            float pulse = 0.7f + 0.3f * Mth.sin(t * (d.isCharging() ? 0.8f : 0.25f));
            DbzTheme.arc(g, cx, cy, 24, 40, 0, 360, DbzTheme.withAlpha(aura, (int) (160 * pulse)), DbzTheme.withAlpha(aura, 0));
        }
        DbzTheme.arc(g, cx, cy, 0, 20, 0, 360, DbzTheme.withAlpha(DbzTheme.darken(accent, 0.35f), 255), 0xFF06070C);
        int c0 = (int) ((cx - 19) * hudScale), c1 = (int) ((cy - 19) * hudScale), c2 = (int) ((cx + 19) * hudScale), c3 = (int) ((cy + 19) * hudScale);
        PortraitRenderer.draw(g, mc.player, cx, cy + 5, 44f, 14f, new int[]{c0, c1, c2, c3});
        RenderSystem.enableBlend();
        hd(g, FRAMES, px, py, frame * 256, 0, 256, 256, 1536, 512);                      // metal
        tint(g, accent, held ? 0.8f + 0.2f * Mth.sin(t * 0.3f) : 0.9f);
        hd(g, FRAMES, px, py, frame * 256, 256, 256, 256, 1536, 512);                    // glow, in the aura colour
        g.setColor(1, 1, 1, 1);
        if (held) {                                                                        // a glint running round the rim
            float a = (t * 4) % 360;
            DbzTheme.arc(g, cx, cy, 25.2f, 27.8f, a, 40, 0x00FFFFFF, 0x00FFFFFF);
            DbzTheme.arc(g, cx, cy, 25.2f, 27.8f, a + 40, 14, 0x90FFFFFF, 0x90FFFFFF);
        }
        float release = d.getReleasePercent() / 100f;                                     // the gauge in the track
        DbzTheme.arc(g, cx, cy, 22.2f, 24.8f, -90, 360 * Math.min(1f, release), 0xFFB0500E, DbzTheme.ACCENT);
        if (release > 1f) DbzTheme.arc(g, cx, cy, 22.2f, 24.8f, -90, 360 * Math.min(1f, release - 1f), 0xFF901010, 0xFFFF4030);

        // ---------------------------------------------------------- bars, plugged into the frame
        int bx = px + 56, by = py + 9;
        float bodyFrac = frac(d.getBody(), s.maxBody());
        int bodyColor = bodyFrac < 0.25f ? DbzTheme.mix(DbzTheme.BODY, 0xFFFFD0C0, 0.5f + 0.5f * Mth.sin(t * 0.6f)) : DbzTheme.BODY;
        int kiColor = held ? DbzTheme.mix(DbzTheme.KI, aura, 0.25f) : DbzTheme.KI;
        float flow = d.isCharging() ? 3.5f : held ? 1.8f : 1f;
        by = zbar(g, font, 0, bx, by, 132, bodyFrac, d.getBody(), bodyColor, t, dt, 0.8f);
        by = zbar(g, font, 1, bx + 2, by, 120, frac(d.getKi(), s.maxKi()), d.getKi(), kiColor, t, dt, flow);
        by = zbar(g, font, 2, bx + 4, by, 108, frac(d.getStamina(), s.maxStamina()), d.getStamina(), DbzTheme.STAMINA, t, dt, 1.2f);
        if (d.isGuarding() || d.getGuardMeter() < 100) {
            float gf = (float) (d.getGuardMeter() / 100);
            int gc = gf < 0.3f ? DbzTheme.mix(0xFFAEE6FF, 0xFFFF6A5A, 0.5f + 0.5f * Mth.sin(t * 0.8f)) : 0xFFAEE6FF;
            int gx = bx + 16, gw = 84;
            g.fill(gx - 1, by, gx + gw + 1, by + 4, 0xF0040508);
            g.fill(gx, by + 1, gx + (int) (gw * gf), by + 3, gc);
            for (int i = 0; i < 2; i++) {
                int pc = i < d.combat().breakerCharges ? 0xFFFFD27A : 0x60FFFFFF;
                DbzTheme.quad(g, gx + gw + 5 + i * 6, by - 1, gx + gw + 8 + i * 6, by + 2, gx + gw + 5 + i * 6, by + 5, gx + gw + 2 + i * 6, by + 2, pc, pc, pc, pc);
            }
            by += 6;
        }

        // ---------------------------------------------------------- battle power and the form plate
        int ly = Math.max(by + 1, py + 46);
        int lx = bx + 14;
        long bp = StatCalculator.battlePower(d);
        String bps = String.format("%,d", bp), rel = d.getReleasePercent() + "%";
        int bw = 16 + font.width(bps) + 8 + (int) (font.width(rel) * 0.7f) + 6;
        DbzTheme.nine(g, BARS, 512, 256, lx, ly, bw, 13, 0, 128, 192, 40, 12, true);
        DbzTheme.text(g, font, Component.translatable("hud.dbzenith.bp"), lx + 5, ly + 4, DbzTheme.DIM, 0.7f);
        g.drawString(font, bps, lx + 16, ly + 3, held ? DbzTheme.brighten(aura, 1.1f) : DbzTheme.TITLE, true);
        DbzTheme.text(g, font, rel, lx + 16 + font.width(bps) + 8, ly + 4, release > 1f ? 0xFFFF6040 : DbzTheme.ACCENT, 0.7f);
        ly += 15;
        if (!form.isBase()) {
            Component name = Component.translatable(form.translationKey());
            String mastery = String.format("M%.0f%%", d.getMastery(form.id()));
            int nw = font.width(name), fw = nw + (int) (font.width(mastery) * 0.7f) + 18;
            DbzTheme.nine(g, BARS, 512, 256, lx, ly, fw, 13, 0, 128, 192, 40, 12, true);
            DbzTheme.hGradient(g, lx + 2, ly + 2, lx + fw - 2, ly + 11, DbzTheme.withAlpha(aura, 150), DbzTheme.withAlpha(aura, 20));
            g.drawString(font, name, lx + 6, ly + 3, 0xFFFFFFFF, true);
            DbzTheme.text(g, font, mastery, lx + 10 + nw, ly + 4, 0xE0FFFFFF, 0.7f);
            ly += 15;
        }
        chipLeft = lx;
        return Math.max(ly + 1, py + 70);
    }

    /** One energy bar: an ornate housing with an icon socket, a flowing fill, the ghost of recent loss and a hot leading edge. */
    private static int zbar(GuiGraphics g, Font font, int i, int x, int y, int w, float frac, double value, int color, float t, float dt, float flow) {
        ease(i, frac, t, dt);
        float fx0 = x + 11, fw = w - 21, fy = y + 3, fh = 6;
        g.fill(x + 11, y + 3, x + w - 8, y + 9, 0xF0080A12);
        if (GHOST[i] > SHOWN[i] + 0.002f) DbzTheme.hGradient(g, fx0, fy, fx0 + fw * GHOST[i], fy + fh, 0xFFF4E8E0, 0xFFE8D0C8);
        float ww = fw * SHOWN[i];
        if (ww > 0.5f) {
            DbzTheme.hGradient(g, fx0, fy, fx0 + ww, fy + fh, DbzTheme.darken(color, 0.55f), color);
            tint(g, color, 0.8f);
            g.pose().pushPose();
            g.pose().translate(fx0, fy, 0);
            g.pose().scale(0.25f, 0.25f, 1);
            g.blit(ENERGY, 0, 0, -(t * 1.6f * flow + i * 37) % 128 + 128, 0, (int) (ww * 4), 24, 128, 24);
            g.pose().popPose();
            g.setColor(1, 1, 1, 1);
            float head = Math.min(6, ww);
            DbzTheme.hGradient(g, fx0 + ww - head, fy, fx0 + ww, fy + fh, 0x00FFFFFF, 0xB0FFFFFF);
            float sweep = ((t * 2.2f + i * 40) % 260) - 30;                                   // a highlight passing along
            if (sweep > 0 && sweep < ww - 6) DbzTheme.hGradient(g, fx0 + sweep, fy, fx0 + sweep + 6, fy + fh, 0x00FFFFFF, 0x50FFFFFF);
        }
        for (int k = 1; k < 4; k++) g.fill((int) (fx0 + fw * k / 4f), y + 4, (int) (fx0 + fw * k / 4f) + 1, y + 8, 0x40000000);
        g.pose().pushPose();                                                                  // the housing, three-sliced
        g.pose().translate(x, y, 0);
        g.pose().scale(0.25f, 0.25f, 1);
        int W = w * 4;
        g.blit(BARS, 0, 0, 0, 0, 56, 48, 512, 256);
        g.blit(BARS, 56, 0, W - 120, 48, 56, 0, 392, 48, 512, 256);
        g.blit(BARS, W - 64, 0, 64, 48, 448, 0, 64, 48, 512, 256);
        g.pose().popPose();
        tint(g, frac > 0 ? color : 0xFF606060, 1f);
        hd(g, BARS, x + 2, y + 2, i * 32, 64, 32, 32, 512, 256);
        g.setColor(1, 1, 1, 1);
        DbzTheme.text(g, font, compact(value), x + w + 2, y + 3, DbzTheme.TEXT, 0.75f);
        return y + 11;
    }

    /**
     * HUD v3, the default (CX-16c): clean and quiet. A rounded portrait card, a battle-power line over three slim bars
     * (crisp vertical gradients with a gloss line, a bright leading edge, quarter ticks, a pale trail of recent loss and
     * the value beside each), all over a soft shade so it reads on any sky. Returns the y of the status chip row.
     */
    private static int clean(GuiGraphics g, Font font, Minecraft mc, PlayerData d, DerivedStats s, Form form, boolean held, int aura,
                             float t, float dt, float hudScale) {
        int px = 6, py = 6, ps = 32;
        DbzTheme.hGradient(g, 0, 0, 210, 52, 0x60000000, 0x00000000);                    // a soft shade under the cluster

        // the portrait card
        if (held || d.isCharging()) {
            float pulse = 0.55f + 0.45f * Mth.sin(t * (d.isCharging() ? 0.8f : 0.25f));
            Ui.round(g, px - 2, py - 2, ps + 4, ps + 4, 2, DbzTheme.withAlpha(aura, (int) (150 * pulse)));
        }
        Ui.round(g, px, py, ps, ps, 2, 0xE00C111C);
        g.fillGradient(px + 1, py + ps / 2, px + ps - 1, py + ps - 1, 0x00000000, DbzTheme.withAlpha(held ? aura : 0x3A5A9A, 90));
        int c0 = (int) ((px + 1) * hudScale), c1 = (int) ((py + 1) * hudScale), c2 = (int) ((px + ps - 1) * hudScale), c3 = (int) ((py + ps - 1) * hudScale);
        PortraitRenderer.draw(g, mc.player, px + ps / 2, py + ps / 2 + 3, 36f, 14f, new int[]{c0, c1, c2, c3});
        RenderSystem.enableBlend();
        Ui.outline(g, px, py, ps, ps, 2, held ? DbzTheme.withAlpha(aura, 230) : 0x50FFFFFF);
        float release = d.getReleasePercent() / 100f;                                     // the release, as a thin line under the card
        g.fill(px + 1, py + ps + 2, px + ps - 1, py + ps + 4, 0x90000000);
        g.fill(px + 1, py + ps + 2, px + 1 + (int) ((ps - 2) * Math.min(1, release)), py + ps + 4, release > 1f ? 0xFFFF4030 : Ui.GOLD);

        // battle power, release and the form, in one line
        int bx = px + ps + 6;
        long bp = StatCalculator.battlePower(d);
        String bps = bp < 0 ? "???" : String.format("%,d", bp);
        DbzTheme.text(g, font, "BP", bx, py, Ui.MUTED, 0.65f);
        float tx = bx + font.width("BP") * 0.65f + 3;
        DbzTheme.text(g, font, bps, tx, py - 0.5f, held ? DbzTheme.brighten(aura, 1.15f) : 0xFFFFFFFF, 0.85f);
        tx += font.width(bps) * 0.85f + 4;
        DbzTheme.text(g, font, d.getReleasePercent() + "%", tx, py, release > 1f ? 0xFFFF6040 : Ui.GOLD, 0.65f);
        tx += font.width(d.getReleasePercent() + "%") * 0.65f + 5;
        if (!form.isBase()) {
            Component name = Component.translatable(form.translationKey());
            DbzTheme.text(g, font, Component.literal(name.getString().toUpperCase()), tx, py, DbzTheme.brighten(aura, 1.2f), 0.65f);
        }

        // the three bars
        float bodyFrac = frac(d.getBody(), s.maxBody());
        boolean low = bodyFrac < 0.25f;
        int hp = low ? DbzTheme.mix(0xFFE84A3C, 0xFFFFC0B0, 0.5f + 0.5f * Mth.sin(t * 0.6f)) : 0xFFE84A3C;
        int ki = held ? DbzTheme.mix(0xFF39C6FF, aura, 0.35f) : 0xFF39C6FF;
        int y = py + 9;
        y = cbar(g, font, 0, bx, y, 124, 7, bodyFrac, d.getBody(), hp, t, dt, low, false);
        y = cbar(g, font, 1, bx, y, 112, 5, frac(d.getKi(), s.maxKi()), d.getKi(), ki, t, dt, false, d.isCharging());
        y = cbar(g, font, 2, bx, y, 98, 3, frac(d.getStamina(), s.maxStamina()), d.getStamina(), 0xFFF2B33A, t, dt, false, false);
        if (d.isGuarding() || d.getGuardMeter() < 100) {                                  // the guard, and the breaker charges
            float gf = (float) (d.getGuardMeter() / 100);
            int gc = gf < 0.3f ? DbzTheme.mix(0xFFAEE6FF, 0xFFFF6A5A, 0.5f + 0.5f * Mth.sin(t * 0.8f)) : 0xFFAEE6FF;
            Ui.round(g, bx, y, 84, 3, 1, 0xA0080B12);
            g.fill(bx, y, bx + (int) (84 * gf), y + 3, gc);
            for (int i = 0; i < 2; i++) Ui.round(g, bx + 88 + i * 5, y - 1, 4, 4, 1, i < d.combat().breakerCharges ? Ui.GOLD : 0x40FFFFFF);
            y += 6;
        }
        chipLeft = bx;
        return Math.max(y + 2, py + ps + 7);
    }

    /** A clean bar: glass track, vertical-gradient fill with a gloss line and a bright edge, a pale trail of loss, quarter ticks, the value. */
    private static int cbar(GuiGraphics g, Font font, int i, int x, int y, int w, int h, float frac, double value, int color, float t, float dt,
                            boolean alarm, boolean charging) {
        ease(i, frac, t, dt);
        if (alarm) Ui.outline(g, x - 1, y - 1, w + 2, h + 2, 1, DbzTheme.withAlpha(color, (int) (120 + 100 * Mth.sin(t * 0.6f))));
        Ui.round(g, x, y, w, h, 1, 0xB0080B12);
        float shown = w * SHOWN[i], ghost = w * GHOST[i];
        if (ghost > shown + 0.5f) g.fill(x + (int) shown, y, x + (int) ghost, y + h, DbzTheme.withAlpha(DbzTheme.mix(color, 0xFFFFFFFF, 0.55f), 200));
        if (shown >= 1) {
            g.fillGradient(x, y, x + (int) shown, y + h, DbzTheme.brighten(color, 1.18f), DbzTheme.darken(color, 0.72f));
            g.fill(x, y, x + (int) shown, y + 1, 0x55FFFFFF);                              // gloss
            g.fill(x + (int) shown - 1, y, x + (int) shown, y + h, 0xB0FFFFFF);           // the leading edge
            if (charging) {                                                                // light running along while charging
                float band = (t * 4 + i * 30) % (shown + 24) - 12;
                DbzTheme.hGradient(g, x + Math.max(0, band - 10), y, x + Math.min(shown, band), y + h, 0x00FFFFFF, 0x70FFFFFF);
                DbzTheme.hGradient(g, x + Math.max(0, band), y, x + Math.min(shown, band + 10), y + h, 0x70FFFFFF, 0x00FFFFFF);
            }
        }
        if (h >= 5) for (int k = 1; k < 4; k++) g.fill(x + w * k / 4, y + 1, x + w * k / 4 + 1, y + h - 1, 0x38000000);   // quarter ticks
        Ui.outline(g, x, y, w, h, 1, 0x30FFFFFF);
        DbzTheme.text(g, font, compact(value), x + w + 3, y + h / 2f - 3f, 0xFFF0F2F6, 0.65f);
        return y + h + 3;
    }

    /** Thin bars and numbers in a corner, no portrait. Returns the y of the status chip row. */
    private static int minimal(GuiGraphics g, Font font, PlayerData d, DerivedStats s, Form form, boolean held, int aura, float t, float dt) {
        int x = 6, y = 6, w = 92;
        float[] fr = {frac(d.getBody(), s.maxBody()), frac(d.getKi(), s.maxKi()), frac(d.getStamina(), s.maxStamina())};
        double[] val = {d.getBody(), d.getKi(), d.getStamina()};
        int[] col = {fr[0] < 0.25f ? DbzTheme.mix(DbzTheme.BODY, 0xFFFFD0C0, 0.5f + 0.5f * Mth.sin(t * 0.6f)) : DbzTheme.BODY,
                held ? DbzTheme.mix(DbzTheme.KI, aura, 0.25f) : DbzTheme.KI, DbzTheme.STAMINA};
        for (int i = 0; i < 3; i++) {
            ease(i, fr[i], t, dt);
            g.fill(x - 1, y - 1, x + w + 1, y + 3, 0x90000000);
            if (GHOST[i] > SHOWN[i] + 0.002f) g.fill(x, y, x + (int) (w * GHOST[i]), y + 2, 0xC0F4E8E0);
            g.fill(x, y, x + (int) (w * SHOWN[i]), y + 2, col[i]);
            DbzTheme.text(g, font, compact(val[i]), x + w + 3, y - 1, DbzTheme.TEXT, 0.6f);
            y += 5;
        }
        y += 1;
        String bp = String.format("%,d", StatCalculator.battlePower(d)) + "  " + d.getReleasePercent() + "%";
        DbzTheme.text(g, font, bp, x, y, held ? DbzTheme.brighten(aura, 1.1f) : DbzTheme.TITLE, 0.7f);
        y += 7;
        if (!form.isBase()) {
            DbzTheme.text(g, font, Component.translatable(form.translationKey()), x, y, DbzTheme.brighten(0xFF000000 | aura, 1.1f), 0.7f);
            y += 7;
        }
        chipLeft = x;
        return y + 2;
    }

    /** Eases a bar: drops at once on loss and leaves a ghost that drains after a moment; rises smoothly. */
    private static void ease(int i, float frac, float t, float dt) {
        if (frac < SHOWN[i]) {
            if (GHOST[i] < SHOWN[i]) GHOST[i] = SHOWN[i];
            SHOWN[i] = frac;
            HOLD_UNTIL[i] = t + 12;
        } else {
            SHOWN[i] += (frac - SHOWN[i]) * Math.min(1f, dt * 0.35f);
        }
        if (t > HOLD_UNTIL[i]) GHOST[i] = Math.max(SHOWN[i], GHOST[i] - dt * 0.025f);
        if (GHOST[i] < SHOWN[i]) GHOST[i] = SHOWN[i];
    }

    /** The backing of the technique and skill chips by the hotbar. */
    private static void chipBack(GuiGraphics g, int x, int y, int w, int h) {
        if (hudStyle() == 0 || hudStyle() == 4) {                                                            // glass, like the rest of the clean HUD
            Ui.round(g, x - 1, y - 1, w + 2, h + 2, 2, 0xC80B101A);
            Ui.outline(g, x - 1, y - 1, w + 2, h + 2, 2, 0x30FFFFFF);
            return;
        }
        if (hudStyle() == 3) {
            DbzTheme.nine(g, BARS, 512, 256, x - 1, y - 1, w + 2, h + 2, 0, 128, 192, 40, 12, true);
            return;
        }
        DbzTheme.slant(g, x - 1, y - 1, w + 2, h + 2, 6, 0xF0040508, 0xF0040508);
        DbzTheme.slant(g, x, y, w, h, 6, 0xE0182238, 0xE00A0E18);
    }

    private static int chip(GuiGraphics g, Font font, int x, int y, Component text, int color) {
        int w = (int) (font.width(text) * 0.75f) + 6;
        if (hudStyle() == 0 || hudStyle() == 4) {                                                            // a glass pill
            Ui.round(g, x, y - 1, w, 9, 2, 0xB80B101A);
            Ui.outline(g, x, y - 1, w, 9, 2, DbzTheme.withAlpha(color, 110));
            DbzTheme.text(g, font, text, x + 3, y + 1, color, 0.75f);
            return x + w + 2;
        }
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
