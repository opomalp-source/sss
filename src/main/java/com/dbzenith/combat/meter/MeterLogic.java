package com.dbzenith.combat.meter;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.Kaioken;
import com.dbzenith.transform.UltraInstinct;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * The PvP meters at work (CX-20), all on the server. Only in PvP mode:
 * <ul>
 *   <li><b>Filling:</b> blows dealt and taken (by the share of the victim's health), perfect guards, vanishes, counters,
 *       long combos, charging; the amounts are {@link MeterRules}'.</li>
 *   <li><b>Gating</b> ({@code [pvp] meterGate}): J only goes to a form whose stud the form bar has reached (the highest
 *       one reached, if the next is out of reach); O only raises Kaioken to a reached stage, and enters Ultra Instinct
 *       only once its stud is reached. Outside PvP you transform freely.</li>
 *   <li><b>Draining:</b> the form bar drains while you hold a form on it (faster for higher tiers), the technique bar
 *       while Kaioken or Ultra Instinct is on, and both decay after a while without fighting. A form holds while the
 *       bar stays above the stud below it; at that stud you drop to the form below (at 0, to base). Kaioken and Ultra
 *       Instinct likewise.</li>
 *   <li><b>Switching:</b> going into PvP while already transformed fills the bars to what you hold (you bring your power
 *       in, and it drains from there); leaving PvP empties them.</li>
 * </ul>
 */
public final class MeterLogic {
    private MeterLogic() {}

    /** Whether forms and techniques need the meters right now. */
    public static boolean gated(PlayerData d) {
        return d.isPvp() && DBZConfig.SERVER.pvpMeterGate.get();
    }

    // ------------------------------------------------------------------ filling

    /** A blow landed: {@code share} of the victim's health (0..1). Either side may be a player in PvP mode. */
    public static void onBlow(LivingEntity attacker, LivingEntity victim, double share) {
        if (share <= 0 || attacker == victim) return;
        double pct = Math.min(100, share * 100);
        MeterRules.Rules r = MeterRules.get();
        PlayerData ad = data(attacker), vd = data(victim);
        if (ad != null && ad.isPvp()) add(ad, r.form().perPercentDealt() * pct, r.tech().perPercentDealt() * pct);
        if (vd != null && vd.isPvp() && attacker != null) add(vd, r.form().perPercentTaken() * pct, r.tech().perPercentTaken() * pct);
    }

    public static void onPerfectGuard(LivingEntity defender) {
        MeterRules.Rules r = MeterRules.get();
        gain(defender, r.form().perfectGuard(), r.tech().perfectGuard());
    }

    public static void onVanish(LivingEntity victim) {
        MeterRules.Rules r = MeterRules.get();
        gain(victim, r.form().vanish(), r.tech().vanish());
    }

    public static void onCounter(LivingEntity attacker) {
        MeterRules.Rules r = MeterRules.get();
        gain(attacker, r.form().counter(), r.tech().counter());
    }

    /** A combo reached {@code hits}: every {@code combo_every} hits pays. */
    public static void onComboHit(LivingEntity attacker, int hits) {
        MeterRules.Rules r = MeterRules.get();
        double f = r.form().comboEvery() > 0 && hits > 0 && hits % r.form().comboEvery() == 0 ? r.form().combo() : 0;
        double t = r.tech().comboEvery() > 0 && hits > 0 && hits % r.tech().comboEvery() == 0 ? r.tech().combo() : 0;
        gain(attacker, f, t);
    }

    private static void gain(LivingEntity e, double form, double tech) {
        PlayerData d = data(e);
        if (d != null && d.isPvp()) add(d, form, tech);
    }

    public static void add(PlayerData d, double form, double tech) {
        if (form != 0) d.setFormMeter(d.getFormMeter() + form);
        if (tech != 0) d.setTechMeter(d.getTechMeter() + tech);
    }

    private static PlayerData data(LivingEntity e) {
        return e instanceof Player p ? ModCapabilities.get(p).orElse(null) : null;
    }

    // ------------------------------------------------------------------ switching PvP

    /** PvP mode went on (the bars rise to cover what you already hold) or off (they empty). */
    public static void onPvp(ServerPlayer p, PlayerData d, boolean on) {
        if (!on) {
            d.setFormMeter(0);
            d.setTechMeter(0);
            return;
        }
        Form cur = Forms.byId(d.getFormId());
        for (Meters.Step s : Meters.formSteps(d)) if (s.id().equals(cur.id())) d.setFormMeter(Math.max(d.getFormMeter(), s.at()));
        List<Meters.Step> tech = Meters.techSteps(d);
        if (UltraInstinct.isIn(d)) {
            for (Meters.Step s : tech) if (s.stage() == 0) d.setTechMeter(Math.max(d.getTechMeter(), s.at()));
        } else if (d.getKaiokenStage() > 0) {
            Meters.Step band = kaiokenBand(tech, d.getKaiokenStage());
            if (band != null) d.setTechMeter(Math.max(d.getTechMeter(), band.at()));
        }
    }

    // ------------------------------------------------------------------ every tick

    /** From KiTicker, every tick: charging, draining, decay, and dropping what the bars no longer hold. */
    public static void tick(ServerPlayer p, PlayerData d, long now) {
        if (!d.isPvp()) {
            if (d.getFormMeter() > 0 || d.getTechMeter() > 0) onPvp(p, d, false);
            return;
        }
        MeterRules.Rules r = MeterRules.get();
        double dt = 1 / 20.0, form = 0, tech = 0;
        if (d.isCharging()) {
            form += r.form().chargingPerSecond() * dt;
            tech += r.tech().chargingPerSecond() * dt;
        }
        Form cur = Forms.byId(d.getFormId());
        if (!cur.isBase() && !Meters.isTechnique(cur)) form -= (r.form().drainPerSecond() + r.form().drainPerTier() * cur.tier()) * dt;
        if (d.getKaiokenStage() > 0 || UltraInstinct.isIn(d)) tech -= (r.tech().drainPerSecond() + r.tech().drainPerTier() * d.getKaiokenStage()) * dt;
        long idle = now - d.getLastCombatTick();
        if (idle > r.form().decayAfterSeconds() * 20) form -= r.form().decayPerSecond() * dt;
        if (idle > r.tech().decayAfterSeconds() * 20) tech -= r.tech().decayPerSecond() * dt;
        add(d, form, tech);
        if (gated(d) && now % 5 == 0) hold(p, d);
    }

    /** Drops a form, a Kaioken stage or Ultra Instinct the bars no longer hold. */
    public static void hold(ServerPlayer p, PlayerData d) {
        Form cur = Forms.byId(d.getFormId());
        if (!cur.isBase() && !Meters.isTechnique(cur)) {
            List<Meters.Step> steps = Meters.formSteps(d);
            for (int i = 0; i < steps.size(); i++) {
                if (!steps.get(i).id().equals(cur.id())) continue;
                double below = i == 0 ? 0 : steps.get(i - 1).at();
                if (d.getFormMeter() <= below + 1e-6) {
                    FormHandler.enter(p, d, i == 0 ? Forms.BASE : Forms.byId(steps.get(i - 1).id()));
                    p.displayClientMessage(Component.translatable("message.dbzenith.meter_form_fades"), true);
                }
                break;
            }
        }
        List<Meters.Step> tech = Meters.techSteps(d);
        if (UltraInstinct.isIn(d)) {                                            // not layered on Kaioken: it holds until the bar empties
            if (d.getTechMeter() <= 1e-6) {
                FormHandler.revertToBase(p, d);
                p.displayClientMessage(Component.translatable("message.dbzenith.meter_tech_fades"), true);
            }
        } else if (d.getKaiokenStage() > 0) {
            List<Meters.Step> kk = tech.stream().filter(s -> s.stage() > 0).toList();
            int k = 0;
            while (k < kk.size() - 1 && kk.get(k).stage() < d.getKaiokenStage()) k++;
            double below = k == 0 ? 0 : kk.get(k - 1).at();
            if (kk.isEmpty() || d.getTechMeter() <= below + 1e-6) {
                if (k == 0) Kaioken.stop(p, d, false);
                else d.setKaiokenStage(kk.get(k - 1).stage());
                p.displayClientMessage(Component.translatable("message.dbzenith.meter_tech_fades"), true);
            }
        }
    }

    // ------------------------------------------------------------------ gating

    /**
     * J in PvP: {@code next} if the form bar has reached its stud, else the highest form above the current one it has
     * reached, else null (and the player is told how far the bar must fill).
     */
    public static Form gateForm(ServerPlayer p, PlayerData d, Form next) {
        if (!gated(d)) return next;
        List<Meters.Step> steps = Meters.formSteps(d);
        double v = d.getFormMeter();
        for (Meters.Step s : steps) if (s.id().equals(next.id()) && v + 1e-6 >= s.at()) return next;
        int from = -1;
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).id().equals(d.getFormId())) from = i;
        Form best = null;
        for (int i = from + 1; i < steps.size(); i++) if (v + 1e-6 >= steps.get(i).at()) best = Forms.byId(steps.get(i).id());
        if (best != null) return best;
        double need = from + 1 < steps.size() ? steps.get(from + 1).at() : 100;
        p.displayClientMessage(Component.translatable("message.dbzenith.meter_form_low", (int) Math.ceil(need)), true);
        return null;
    }

    /** The highest Kaioken stage the technique bar allows (no limit outside PvP). */
    public static int kaiokenCap(PlayerData d) {
        if (!gated(d)) return Integer.MAX_VALUE;
        int cap = 0;
        for (Meters.Step s : Meters.techSteps(d)) if (s.stage() > 0 && d.getTechMeter() + 1e-6 >= s.at()) cap = Math.max(cap, s.stage());
        return cap;
    }

    /** The stud that covers a Kaioken stage: the first whose stage is at least it (x3 needs the x4 stud). */
    static Meters.Step kaiokenBand(List<Meters.Step> tech, int stage) {
        Meters.Step last = null;
        for (Meters.Step s : tech) {
            if (s.stage() <= 0) continue;
            last = s;
            if (s.stage() >= stage) return s;
        }
        return last;
    }

    /** Tells the player how far the technique bar must fill for {@code stage}. */
    public static void tellKaiokenLow(ServerPlayer p, PlayerData d, int stage) {
        Meters.Step band = kaiokenBand(Meters.techSteps(d), stage);
        p.displayClientMessage(Component.translatable("message.dbzenith.meter_tech_low", band == null ? 100 : (int) Math.ceil(band.at())), true);
    }

    /** Which Ultra Instinct you can enter (Mastered if you have it), or null. */
    public static Form ultraInstinct(PlayerData d) {
        if (d.hasFlag(UltraInstinct.MASTERED_FLAG) && FormHandler.problem(d, Forms.ULTRA_INSTINCT) == null) return Forms.ULTRA_INSTINCT;
        if (d.hasFlag(UltraInstinct.SIGN_FLAG) && FormHandler.problem(d, Forms.ULTRA_INSTINCT_SIGN) == null) return Forms.ULTRA_INSTINCT_SIGN;
        return null;
    }

    /**
     * The technique key (O): Kaioken one stage up; with Ultra Instinct learned, Ultra Instinct once Kaioken can go no
     * higher (or isn't learned), and in PvP straight away once its stud is reached. Ultra Instinct is a technique: J
     * never goes there.
     */
    public static boolean techniqueUp(ServerPlayer p, PlayerData d) {
        Form ui = ultraInstinct(d);
        if (UltraInstinct.isIn(d)) {
            if (ui == Forms.ULTRA_INSTINCT && !Forms.ULTRA_INSTINCT.id().equals(d.getFormId())) return enterUltraInstinct(p, d, ui);
            p.displayClientMessage(Component.translatable("message.dbzenith.ultra_instinct_already"), true);
            return false;
        }
        double uiAt = MeterRules.get().ultraInstinctAt();
        boolean uiReady = !gated(d) || d.getTechMeter() + 1e-6 >= uiAt;
        if (ui != null && gated(d) && uiReady) return enterUltraInstinct(p, d, ui);
        boolean kaiokenMore = Kaioken.maxStage(d) > 0 && d.getKaiokenStage() < Kaioken.maxStage(d) && Kaioken.formAllows(Forms.byId(d.getFormId()));
        if (ui != null && !kaiokenMore) {
            if (uiReady) return enterUltraInstinct(p, d, ui);
            p.displayClientMessage(Component.translatable("message.dbzenith.meter_tech_low", (int) Math.ceil(uiAt)), true);
            return false;
        }
        return Kaioken.raise(p, d);
    }

    /** Shift+O: Kaioken off, or out of Ultra Instinct. */
    public static void techniqueOff(ServerPlayer p, PlayerData d) {
        if (UltraInstinct.isIn(d)) FormHandler.revertToBase(p, d);
        else Kaioken.stop(p, d, false);
    }

    private static boolean enterUltraInstinct(ServerPlayer p, PlayerData d, Form ui) {
        Kaioken.stop(p, d, true);
        if (d.isTransforming()) d.stopTransforming();
        FormHandler.enter(p, d, ui);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.dbzenith.registry.ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.9f, 0.7f);
        return true;
    }
}
