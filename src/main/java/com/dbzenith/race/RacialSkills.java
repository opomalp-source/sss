package com.dbzenith.race;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.RacialSkill.Stat;
import com.dbzenith.race.RacialSkill.When;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.transform.Forms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every racial skill, and the math that applies their modifiers. Passives are folded into derived stats through
 * the live condition mask that {@link RacialSkillEffects#tick} keeps up to date; blow-time conditions (the foe, the
 * kind of hit) are applied in the combat hooks.
 */
public final class RacialSkills {
    private static final Map<String, RacialSkill> ALL = new LinkedHashMap<>();
    private static final Map<Race, Map<Variant, List<RacialSkill>>> BY_CHARACTER = new EnumMap<>(Race.class);

    private RacialSkills() {}

    private static RacialSkill add(RacialSkill.Builder b) {
        RacialSkill s = b.build();
        if (ALL.put(s.id(), s) != null) throw new IllegalStateException("duplicate racial skill " + s.id());
        return s;
    }

    private static final int HUMAN = 0xE8C890, SAIYAN = 0xFFD040, LEGEND = 0x7CFF6A, PRIMAL = 0xD83020, FROST = 0xC070FF,
            HALF = 0xFFE680, NAMEK = 0x60E080, MAJIN = 0xFF80C0, MACHINE = 0x70D0FF, VAMP = 0xC01830, BIO = 0x9AD840,
            TUFFLE = 0xFFA040, ALIEN = 0x40E0C0, KAI = 0xFFF0C0, DEMON = 0x9A40FF;

    static {
        // ---------------------------------------------------------- Human
        add(RacialSkill.passive("persistence").races(Race.HUMAN).level(50).color(HUMAN)
                .mod(Stat.DAMAGE_TAKEN, -0.15, When.LOW_BODY));
        add(RacialSkill.passive("second_wind").races(Race.HUMAN).level(200).color(HUMAN));
        add(RacialSkill.active("sheer_willpower").races(Race.HUMAN).level(400).color(HUMAN).use(10, 120, 20)
                .mod(Stat.POWER, 0.15, When.ACTIVE));
        add(RacialSkill.passive("mentors_teaching").only(Variant.ANCIENT_HERMIT).level(150).color(HUMAN)
                .mod(Stat.TP_GAIN, 0.15).mod(Stat.MASTERY_GAIN, 0.2));
        add(RacialSkill.passive("no_wasted_motion").only(Variant.PEAK_HUMAN).level(150).color(HUMAN)
                .mod(Stat.KI_COST, -0.15).mod(Stat.SPEED, 0.08));
        add(RacialSkill.passive("third_eye").only(Variant.TRICLOPS).level(150).color(HUMAN)
                .mod(Stat.DAMAGE_DEALT, 0.12, When.FOE_STRONGER).mod(Stat.KI, 0.05));

        // ---------------------------------------------------------- Saiyan (every lineage)
        add(RacialSkill.passive("warrior_race").races(Race.SAIYAN).level(50).color(SAIYAN)
                .mod(Stat.POWER, 0.08, When.IN_COMBAT));
        add(RacialSkill.passive("false_advantage").races(Race.SAIYAN).level(100).color(SAIYAN)
                .mod(Stat.DAMAGE_DEALT, 0.10, When.BASE_FORM));
        add(RacialSkill.passive("seasoned_warrior").races(Race.SAIYAN).level(150).color(SAIYAN)
                .mod(Stat.TP_GAIN, 0.25, When.IN_COMBAT));
        add(RacialSkill.passive("prideful").races(Race.SAIYAN).level(300).color(SAIYAN)
                .mod(Stat.DAMAGE_DEALT, 0.15, When.FOE_STRONGER));
        add(RacialSkill.active("saiyans_resolve").races(Race.SAIYAN).level(500).color(SAIYAN).use(8, 90, 15)
                .mod(Stat.DAMAGE_TAKEN, -0.30, When.ACTIVE).mod(Stat.KNOCKBACK_RESIST, 1.0, When.ACTIVE));
        // Legendary
        add(RacialSkill.passive("overflowing_power").only(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL).level(100).color(LEGEND)
                .mod(Stat.KI_REGEN, 0.5));
        add(RacialSkill.passive("unstoppable_force").only(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL).level(300).color(LEGEND)
                .mod(Stat.KNOCKBACK_RESIST, 0.8, When.TRANSFORMED));
        add(RacialSkill.active("venting").only(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL).level(400).color(LEGEND).use(15, 45, 0));
        add(RacialSkill.passive("escalating_power").only(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL).level(600).color(LEGEND)
                .mod(Stat.POWER, 0.15, When.COMBAT_RAMP));
        // Primal
        add(RacialSkill.passive("primal_zenkai").only(Variant.PRIMAL, Variant.LEGENDARY_PRIMAL).level(100).color(PRIMAL));
        add(RacialSkill.passive("primal_evolution").only(Variant.PRIMAL, Variant.LEGENDARY_PRIMAL).level(150).color(PRIMAL)
                .mod(Stat.MASTERY_GAIN, 0.3));
        add(RacialSkill.passive("unmatched_ferocity").only(Variant.PRIMAL, Variant.LEGENDARY_PRIMAL).level(200).color(PRIMAL)
                .mod(Stat.MELEE, 0.08).mod(Stat.MELEE, 0.12, When.LOW_BODY));
        add(RacialSkill.active("roaring_evolution").only(Variant.PRIMAL, Variant.LEGENDARY_PRIMAL).level(300).color(PRIMAL).use(8, 60, 0));
        add(RacialSkill.passive("limitless_power").only(Variant.PRIMAL, Variant.LEGENDARY_PRIMAL).level(500).color(PRIMAL)
                .mod(Stat.FORM_DRAIN, -0.20, When.TRANSFORMED));
        add(RacialSkill.active("shattering_the_limit").only(Variant.PRIMAL, Variant.LEGENDARY_PRIMAL).level(800).color(PRIMAL).use(15, 150, 12)
                .mod(Stat.POWER, 0.25, When.ACTIVE).mod(Stat.POWER, -0.15, When.AFTER));

        // ---------------------------------------------------------- Half-Saiyan
        add(RacialSkill.passive("prodigy").races(Race.HALF_SAIYAN).level(0).color(HALF)
                .mod(Stat.TP_GAIN, 0.10));
        add(RacialSkill.passive("hidden_talent").races(Race.HALF_SAIYAN).level(150).color(HALF)
                .mod(Stat.MASTERY_GAIN, 0.5));
        add(RacialSkill.passive("immeasurable_potential").races(Race.HALF_SAIYAN).level(300).color(HALF)
                .mod(Stat.POWER, 0.20, When.LOW_BODY));
        add(RacialSkill.active("blazing_spirit").races(Race.HALF_SAIYAN).level(500).color(HALF).use(5, 120, 15)
                .mod(Stat.KI_REGEN, 2.0, When.ACTIVE));
        add(RacialSkill.passive("unlocked_potential").only(Variant.NEW_GENERATION).level(150).color(HALF)
                .mod(Stat.POWER, 0.12, When.BASE_FORM));
        add(RacialSkill.passive("time_hardened").only(Variant.FUTURE_LINEAGE).level(150).color(HALF)
                .mod(Stat.DAMAGE_DEALT, 0.15, When.HURT_RECENTLY));
        add(RacialSkill.passive("beast_within").only(Variant.AWAKENED_EVOLUTION).level(150).color(PRIMAL)
                .mod(Stat.MELEE, 0.20, When.LOW_BODY));

        // ---------------------------------------------------------- Frost Demon
        add(RacialSkill.passive("vacuum_breathing").races(Race.FROST_DEMON).level(0).color(FROST));
        add(RacialSkill.passive("perfect_control").races(Race.FROST_DEMON).level(100).color(FROST)
                .mod(Stat.KI_COST, -0.15));
        add(RacialSkill.passive("revitalizing_metamorphosis").races(Race.FROST_DEMON).level(200).color(FROST));
        add(RacialSkill.passive("extreme_tenacity").races(Race.FROST_DEMON).level(300).color(FROST)
                .mod(Stat.DAMAGE_TAKEN, -0.30, When.CRITICAL_BODY));
        add(RacialSkill.active("tyrants_glare").races(Race.FROST_DEMON).level(400).color(FROST).use(6, 45, 0));
        add(RacialSkill.passive("unrestrained_wrath").races(Race.FROST_DEMON).level(500).color(FROST)
                .mod(Stat.KI, 0.20, When.LOW_BODY));
        add(RacialSkill.passive("sadistic_nature").only(Variant.MUTANT).level(100).color(0xFF1060)
                .mod(Stat.KI_ON_HIT, 0.02));
        add(RacialSkill.passive("ruthless_mind").only(Variant.MUTANT).level(200).color(0xFF1060)
                .mod(Stat.DAMAGE_DEALT, 0.20, When.FOE_WEAK));
        add(RacialSkill.passive("long_awaited_100").only(Variant.MUTANT).level(300).color(0xFF1060)
                .mod(Stat.POWER, 0.10, When.TRANSFORMED));
        add(RacialSkill.passive("metal_plating").only(Variant.METAL).level(100).color(0xC0D0E0)
                .mod(Stat.DAMAGE_TAKEN, -0.10));
        add(RacialSkill.passive("auto_repair_core").only(Variant.METAL).level(300).color(0xC0D0E0)
                .mod(Stat.BODY_REGEN, 0.6, When.LOW_BODY));

        // ---------------------------------------------------------- Namekian
        add(RacialSkill.passive("vitality_restoration").races(Race.NAMEKIAN).level(0).color(NAMEK)
                .mod(Stat.BODY_REGEN, 0.5));
        add(RacialSkill.passive("limb_regeneration").races(Race.NAMEKIAN).level(150).color(NAMEK));
        add(RacialSkill.passive("namekian_resilience").races(Race.NAMEKIAN).level(300).color(NAMEK)
                .mod(Stat.DAMAGE_TAKEN, -0.08));
        add(RacialSkill.active("spirit_disruption").races(Race.NAMEKIAN).level(400).color(NAMEK).use(10, 60, 0));
        add(RacialSkill.passive("reincarnation").races(Race.NAMEKIAN).level(700).color(NAMEK));
        add(RacialSkill.passive("warriors_vitality").only(Variant.WARRIOR_CLAN).level(100).color(NAMEK)
                .mod(Stat.MELEE, 0.12, When.FULL_BODY));
        add(RacialSkill.active("dragon_blessing").only(Variant.DRAGON_CLAN).level(200).color(0x80FFC0).use(12, 90, 0));
        add(RacialSkill.passive("demonic_regeneration").only(Variant.DEMON_CLAN).level(100).color(DEMON)
                .mod(Stat.BODY_REGEN, 0.8, When.LOW_BODY));

        // ---------------------------------------------------------- Majin
        add(RacialSkill.passive("gum_body").races(Race.MAJIN).level(0).color(MAJIN)
                .mod(Stat.DAMAGE_TAKEN, -0.20, When.MELEE_HIT));
        add(RacialSkill.passive("elastic_monster").races(Race.MAJIN).level(100).color(MAJIN)
                .mod(Stat.KNOCKBACK_RESIST, 0.5));
        add(RacialSkill.active("remote_absorb").races(Race.MAJIN).level(250).color(MAJIN).use(4, 45, 0));
        add(RacialSkill.passive("death_regeneration").races(Race.MAJIN).level(400).color(MAJIN));
        add(RacialSkill.passive("sycophantic_rage").only(Variant.CORRUPTED).level(200).color(0xB02050)
                .mod(Stat.MELEE, 0.25, When.LOW_BODY));
        add(RacialSkill.passive("inner_madness").only(Variant.CORRUPTED).level(300).color(0xB02050)
                .mod(Stat.POWER, 0.20, When.COMBAT_RAMP).mod(Stat.KI_REGEN, -0.3, When.IN_COMBAT));
        add(RacialSkill.active("mindless_gambit").only(Variant.CORRUPTED).level(400).color(0xB02050).use(5, 90, 15)
                .mod(Stat.DAMAGE_DEALT, 0.30, When.ACTIVE).mod(Stat.DAMAGE_TAKEN, 0.30, When.ACTIVE));

        // ---------------------------------------------------------- Android and Cyborg
        add(RacialSkill.passive("infinite_reactor").races(Race.ANDROID).level(0).color(MACHINE)
                .mod(Stat.KI_REGEN, 0.4));
        add(RacialSkill.passive("organic_core").races(Race.CYBORG).level(0).color(MACHINE)
                .mod(Stat.BODY_REGEN, 0.3));
        add(RacialSkill.passive("targeting_system").races(Race.ANDROID, Race.CYBORG).level(150).color(MACHINE)
                .mod(Stat.KI, 0.08));
        add(RacialSkill.active("self_repair").races(Race.ANDROID, Race.CYBORG).level(200).color(MACHINE).use(10, 90, 0));
        add(RacialSkill.active("overclock").races(Race.ANDROID, Race.CYBORG).level(500).color(MACHINE).use(0, 120, 12)
                .mod(Stat.POWER, 0.20, When.ACTIVE).mod(Stat.SPEED, 0.20, When.ACTIVE).mod(Stat.KI_REGEN, -1.0, When.AFTER));

        // ---------------------------------------------------------- Vampire
        add(RacialSkill.passive("night_stalker").races(Race.VAMPIRE).level(0).color(VAMP)
                .mod(Stat.POWER, 0.20, When.NIGHT));
        add(RacialSkill.passive("sun_scorn").only(Variant.FERAL_BLOOD).level(0).color(VAMP)
                .mod(Stat.POWER, -0.10, When.DAY));
        add(RacialSkill.passive("aristocrats_poise").only(Variant.NOBLE_BLOOD).level(150).color(VAMP)
                .mod(Stat.DAMAGE_TAKEN, -0.12, When.FULL_BODY));
        add(RacialSkill.passive("feral_instinct").only(Variant.FERAL_BLOOD).level(150).color(VAMP)
                .mod(Stat.SPEED, 0.15, When.IN_COMBAT));
        add(RacialSkill.passive("crimson_hunger").races(Race.VAMPIRE).level(200).color(VAMP)
                .mod(Stat.LIFESTEAL, 0.15, When.LOW_BODY));
        add(RacialSkill.active("bat_swarm").races(Race.VAMPIRE).level(300).color(VAMP).use(6, 30, 0));

        // ---------------------------------------------------------- Bio-Android
        add(RacialSkill.passive("regenerating_cells").races(Race.BIO_ANDROID).level(0).color(BIO)
                .mod(Stat.BODY_REGEN, 0.4));
        add(RacialSkill.passive("apex_predator").only(Variant.APEX_STRAIN).level(150).color(BIO)
                .mod(Stat.DAMAGE_DEALT, 0.15, When.FOE_WEAK));
        add(RacialSkill.passive("swarm_vigor").only(Variant.SWARM_STRAIN).level(150).color(BIO)
                .mod(Stat.SPEED, 0.12, When.IN_COMBAT));
        add(RacialSkill.passive("adaptive_cells").races(Race.BIO_ANDROID).level(200).color(BIO)
                .mod(Stat.DAMAGE_TAKEN, -0.15, When.HURT_RECENTLY));
        add(RacialSkill.active("cellular_absorption").races(Race.BIO_ANDROID).level(300).color(BIO).use(6, 60, 0));
        add(RacialSkill.passive("perfect_body").races(Race.BIO_ANDROID).level(500).color(BIO)
                .mod(Stat.POWER, 0.08, When.TRANSFORMED));

        // ---------------------------------------------------------- Tuffle
        add(RacialSkill.passive("machine_mind").races(Race.TUFFLE).level(0).color(TUFFLE)
                .mod(Stat.KI_COST, -0.15));
        add(RacialSkill.passive("tactical_genius").only(Variant.SCIENTIST_CASTE).level(150).color(TUFFLE)
                .mod(Stat.TP_GAIN, 0.15));
        add(RacialSkill.passive("parasite_link").only(Variant.PARASITE_CASTE).level(150).color(TUFFLE)
                .mod(Stat.LIFESTEAL, 0.06));
        add(RacialSkill.active("nano_repair").races(Race.TUFFLE).level(200).color(TUFFLE).use(10, 75, 0));
        add(RacialSkill.passive("revenge_protocol").races(Race.TUFFLE).level(300).color(TUFFLE)
                .mod(Stat.KI, 0.25, When.LOW_BODY));
        add(RacialSkill.active("system_scan").races(Race.TUFFLE).level(400).color(TUFFLE).use(5, 60, 20)
                .mod(Stat.DAMAGE_DEALT, 0.12, When.ACTIVE));

        // ---------------------------------------------------------- Gen Alien
        add(RacialSkill.passive("adaptive_biology").races(Race.GEN_ALIEN).level(0).color(ALIEN)
                .mod(Stat.BODY_REGEN, 0.2));
        add(RacialSkill.passive("dense_body").only(Variant.HEAVYWORLDER).level(100).color(ALIEN)
                .mod(Stat.DAMAGE_TAKEN, -0.12));
        add(RacialSkill.active("void_step").only(Variant.VOIDBORN).level(100).color(0x8060FF).use(4, 12, 0));
        add(RacialSkill.passive("fleet").only(Variant.SWIFTKIN).level(100).color(ALIEN)
                .mod(Stat.SPEED, 0.15));
        add(RacialSkill.passive("adrenal_surge").races(Race.GEN_ALIEN).level(200).color(ALIEN)
                .mod(Stat.SPEED, 0.25, When.LOW_BODY));
        add(RacialSkill.active("seismic_stomp").only(Variant.HEAVYWORLDER).level(300).color(ALIEN).use(8, 40, 0));
        add(RacialSkill.passive("void_touched").only(Variant.VOIDBORN).level(300).color(0x8060FF)
                .mod(Stat.KI, 0.10));
        add(RacialSkill.active("blur").only(Variant.SWIFTKIN).level(300).color(ALIEN).use(6, 45, 8)
                .mod(Stat.SPEED, 0.40, When.ACTIVE).mod(Stat.DAMAGE_TAKEN, -0.15, When.ACTIVE));

        // ---------------------------------------------------------- Core Person
        add(RacialSkill.passive("divine_insight").only(Variant.KAI).level(0).color(KAI)
                .mod(Stat.KI_REGEN, 0.5).mod(Stat.KI, 0.08));
        add(RacialSkill.active("sacred_barrier").only(Variant.KAI).level(300).color(KAI).use(12, 60, 6)
                .mod(Stat.DAMAGE_TAKEN, -0.60, When.ACTIVE));
        add(RacialSkill.passive("demonic_pact").only(Variant.DEMON).level(0).color(DEMON)
                .mod(Stat.POWER, 0.15, When.NIGHT));
        add(RacialSkill.passive("otherworldly_body").races(Race.CORE_PERSON).level(100).color(KAI)
                .mod(Stat.DAMAGE_TAKEN, -0.15, When.KI_HIT));
        add(RacialSkill.passive("kai_mastery").only(Variant.KAI).level(500).color(KAI)
                .mod(Stat.MASTERY_GAIN, 0.4));
        add(RacialSkill.passive("demon_hunger").only(Variant.DEMON).level(150).color(DEMON)
                .mod(Stat.LIFESTEAL, 0.08, When.IN_COMBAT));
        add(RacialSkill.active("dark_aura").only(Variant.DEMON).level(300).color(DEMON).use(8, 90, 15)
                .mod(Stat.POWER, 0.10, When.ACTIVE));
    }

    // ------------------------------------------------------------------ queries

    public static RacialSkill byId(String id) {
        return ALL.get(id);
    }

    public static java.util.Collection<RacialSkill> all() {
        return ALL.values();
    }

    /** Every skill a race and variant has, locked or not, in unlock order. */
    public static List<RacialSkill> forCharacter(Race race, Variant variant) {
        return BY_CHARACTER.computeIfAbsent(race, r -> new EnumMap<>(Variant.class)).computeIfAbsent(variant, v -> {
            List<RacialSkill> out = new ArrayList<>();
            for (RacialSkill s : ALL.values()) if (s.fits(race, v)) out.add(s);
            out.sort(java.util.Comparator.comparingInt(RacialSkill::unlockLevel));
            return List.copyOf(out);
        });
    }

    public static int unlockLevel(RacialSkill s) {
        return (int) Math.round(s.unlockLevel() * DBZConfig.SERVER.unlockLevelScale.get());
    }

    public static boolean unlocked(PlayerData d, RacialSkill s) {
        return s.fits(d.getRace(), d.getVariant()) && StatCalculator.level(d) >= unlockLevel(s);
    }

    public static boolean has(PlayerData d, String id) {
        RacialSkill s = byId(id);
        return s != null && unlocked(d, s);
    }

    // ------------------------------------------------------------------ live conditions

    /** Steps of the three-minute combat ramp kept in the mask (bits 24..27). */
    private static final int RAMP_SHIFT = 24;

    public static int mask(PlayerData d, boolean night, boolean hurtRecently) {
        double max = d.getDerived().maxBody(), maxKi = d.getDerived().maxKi();
        double body = max > 0 ? d.getBody() / max : 1, ki = maxKi > 0 ? d.getKi() / maxKi : 1;
        int m = bit(When.ALWAYS);
        if (body < 0.30) m |= bit(When.LOW_BODY);
        if (body < 0.15) m |= bit(When.CRITICAL_BODY);
        if (body > 0.90) m |= bit(When.FULL_BODY);
        if (d.getCombatTicks() > 0) m |= bit(When.IN_COMBAT);
        if (d.isTransformed()) m |= bit(When.TRANSFORMED);
        else m |= bit(When.BASE_FORM);
        m |= bit(night ? When.NIGHT : When.DAY);
        if (ki > 0.75) m |= bit(When.HIGH_KI);
        if (ki < 0.25) m |= bit(When.LOW_KI);
        if (hurtRecently) m |= bit(When.HURT_RECENTLY);
        int ramp = (int) Math.round(Math.min(1.0, d.getCombatTicks() / 3600.0) * 10);
        return m | ramp << RAMP_SHIFT;
    }

    private static int bit(When w) {
        return 1 << w.ordinal();
    }

    /** How much of a modifier holds right now, from what the fighter alone knows (0..1). */
    static double holds(PlayerData d, RacialSkill s, When w) {
        return switch (w) {
            case ACTIVE -> d.getRacialActive().contains(s.id()) ? 1 : 0;
            case AFTER -> d.getRacialAfter().contains(s.id()) ? 1 : 0;
            case COMBAT_RAMP -> (d.getRacialMask() >>> RAMP_SHIFT & 15) / 10.0;
            default -> w.selfOnly() && (d.getRacialMask() & bit(w)) != 0 ? 1 : 0;
        };
    }

    /** The product of every (1 + amount) for a stat, over unlocked skills, for the self-only conditions. */
    public static double factor(PlayerData d, Stat stat) {
        double f = 1.0;
        for (RacialSkill s : forCharacter(d.getRace(), d.getVariant())) {
            if (s.mods().isEmpty() || !unlocked(d, s)) continue;
            for (RacialSkill.Mod m : s.mods()) {
                if (m.stat() != stat || !m.when().selfOnly()) continue;
                f *= 1.0 + m.amount() * holds(d, s, m.when());
            }
        }
        return Math.max(0, f);
    }

    /** The sum of a stat's amounts (knockback resistance, lifesteal, ki on hit). */
    public static double sum(PlayerData d, Stat stat) {
        double t = 0;
        for (RacialSkill s : forCharacter(d.getRace(), d.getVariant())) {
            if (s.mods().isEmpty() || !unlocked(d, s)) continue;
            for (RacialSkill.Mod m : s.mods()) if (m.stat() == stat && m.when().selfOnly()) t += m.amount() * holds(d, s, m.when());
        }
        return t;
    }

    /** Attribute multiplier from POWER, MELEE (strength) and KI (ki power). */
    public static double attributeFactor(PlayerData d, Attribute a) {
        return switch (a) {
            case STRENGTH -> factor(d, Stat.POWER) * factor(d, Stat.MELEE);
            case DEXTERITY -> factor(d, Stat.POWER);
            case KI_POWER -> factor(d, Stat.POWER) * factor(d, Stat.KI);
            default -> 1.0;
        };
    }

    /**
     * Damage multiplier for a blow, from both sides: the attacker's DAMAGE_DEALT (including foe conditions) and the
     * victim's DAMAGE_TAKEN (including the kind of hit).
     */
    public static double blowFactor(PlayerData attacker, PlayerData victim, double victimBodyFraction, boolean foeStronger, boolean ki) {
        double f = 1.0;
        if (attacker != null) {
            f *= factor(attacker, Stat.DAMAGE_DEALT);
            for (RacialSkill s : forCharacter(attacker.getRace(), attacker.getVariant())) {
                if (!unlocked(attacker, s)) continue;
                for (RacialSkill.Mod m : s.mods()) {
                    if (m.stat() != Stat.DAMAGE_DEALT) continue;
                    boolean on = m.when() == When.FOE_STRONGER && foeStronger || m.when() == When.FOE_WEAK && victimBodyFraction < 0.30;
                    if (on) f *= 1.0 + m.amount();
                }
            }
        }
        if (victim != null) {
            f *= factor(victim, Stat.DAMAGE_TAKEN);
            for (RacialSkill s : forCharacter(victim.getRace(), victim.getVariant())) {
                if (!unlocked(victim, s)) continue;
                for (RacialSkill.Mod m : s.mods()) {
                    if (m.stat() != Stat.DAMAGE_TAKEN) continue;
                    boolean on = m.when() == When.MELEE_HIT && !ki || m.when() == When.KI_HIT && ki;
                    if (on) f *= 1.0 + m.amount();
                }
            }
        }
        return Math.max(0, f);
    }

    /** Whether the condition mask says the fighter is in a given self-only state (for effects and the UI). */
    public static boolean is(PlayerData d, When w) {
        return (d.getRacialMask() & bit(w)) != 0;
    }

    static boolean isNight(net.minecraft.world.level.Level level) {
        long t = level.getDayTime() % 24000;
        return t >= 13000 && t < 23000;
    }
}
