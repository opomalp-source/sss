package com.dbzenith.transform;

import com.dbzenith.data.PlayerData;
import com.dbzenith.race.Variant;
import com.dbzenith.stats.Race;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in forms. Phase 2 ships the Saiyan line as the reference tree; Phase 3 adds one line per race.
 * Unlock levels are scaled by {@code transformations.unlockLevelScale}.
 */
public final class Forms {
    private static final Map<String, Form> BY_ID = new LinkedHashMap<>();

    private static final int GOLD_AURA = 0xFFD23C;
    private static final int GOLD_HAIR = 0xFFE15A;
    private static final int TEAL_EYES = 0x2FD6B5;

    public static final Form BASE = add(Form.builder(PlayerData.BASE_FORM).allowsOverdrive().build());

    public static final Form SUPER_SAIYAN = add(Form.builder("super_saiyan").parent("base", 1)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).except(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL).multiplier(2.0).drain(1.5, 0)
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY).unlock(150, 0).build());

    public static final Form SUPER_SAIYAN_G2 = add(Form.builder("super_saiyan_g2").parent("super_saiyan", 2)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).except(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL).multipliers(2.4, 2.1, 2.25).drain(2.2, 0)
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY).unlock(250, 25).build());

    public static final Form SUPER_SAIYAN_G3 = add(Form.builder("super_saiyan_g3").parent("super_saiyan_g2", 3)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).except(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL).multipliers(3.0, 1.6, 2.4).drain(3.5, 2.0).speedBonus(-0.3)
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY_TALL).unlock(320, 30).build());

    public static final Form SUPER_SAIYAN_2 = add(Form.builder("super_saiyan_2").parent("super_saiyan", 2)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).except(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL).multiplier(3.0).drain(2.5, 0).lightning()
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY_TALL).unlock(500, 50).build());

    public static final Form SUPER_SAIYAN_3 = add(Form.builder("super_saiyan_3").parent("super_saiyan_2", 3)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).except(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL).multiplier(4.0).drain(5.0, 1.0).lightning()
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.LONG).unlock(900, 50).build());

    public static final Form SUPER_SAIYAN_GOD = add(Form.builder("super_saiyan_god").parent("base", 4)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).except(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL, Variant.PRIMAL, Variant.AWAKENED_EVOLUTION).multiplier(5.0).drain(1.0, 0)
            .colors(0xFF3B3B, 0xC8283C, 0xD0263E).calmAura().hair(Form.HairStyle.SLIM).unlock(1200, 0).requiresFlag("god_ki").build());

    public static final Form SUPER_SAIYAN_BLUE = add(Form.builder("super_saiyan_blue").parent("super_saiyan_god", 5)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).except(Variant.LEGENDARY, Variant.LEGENDARY_PRIMAL, Variant.PRIMAL, Variant.AWAKENED_EVOLUTION, Variant.FUTURE_LINEAGE).multiplier(6.0).drain(3.0, 0).allowsOverdrive()
            .colors(0x36B8FF, 0x4FC3FF, 0x2E7DFF).calmAura().hair(Form.HairStyle.SPIKY).unlock(1800, 50).build());

    public static final Form GREAT_APE = add(Form.builder("great_ape").parent("base", 1)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multipliers(4.0, 0.6, 2.0).drain(0, 0)
            .colors(0xB0402A, -1, 0xFF2020).scale(3.0f).noTechniques().noFlight().trigger(Form.Trigger.MOON).build());

    // --- Half-Saiyan exclusive (also uses the Saiyan line above) ---
    public static final Form ULTIMATE = add(Form.builder("ultimate").parent("base", 4)
            .races(Race.HALF_SAIYAN).except(Variant.FUTURE_LINEAGE, Variant.AWAKENED_EVOLUTION).multiplier(4.5).drain(0.6, 0)
            .colors(0xFFFFFF, -1, -1).calmAura().unlock(1000, 0).requiresFlag("potential_unlocked").build());

    // --- Human ---
    public static final Form FULL_POWER = add(Form.builder("full_power").parent("base", 1)
            .races(Race.HUMAN).multiplier(1.5).drain(0.8, 0).colors(0xF2F6FF, -1, -1).unlock(100, 0).build());
    public static final Form BUFFED = add(Form.builder("buffed").parent("full_power", 2)
            .races(Race.HUMAN).except(Variant.ANCIENT_HERMIT, Variant.TRICLOPS).multipliers(2.8, 1.8, 2.4).drain(1.5, 1.0).speedBonus(-0.15)
            .colors(0xFFE8C0, -1, -1).unlock(300, 30).build());
    public static final Form POTENTIAL_UNLEASHED = add(Form.builder("potential_unleashed").parent("full_power", 3)
            .races(Race.HUMAN).only(Variant.HUMAN).multiplier(3.4).drain(1.0, 0).colors(0xFFFFFF, -1, 0x9FD8FF).unlock(700, 50).build());

    // --- Namekian ---
    public static final Form GIANT_NAMEKIAN = add(Form.builder("giant_namekian").parent("base", 1)
            .races(Race.NAMEKIAN).multipliers(2.5, 0.8, 1.5).drain(2.0, 0).scale(2.5f)
            .colors(0xB8FFB0, -1, -1).unlock(150, 0).build());
    public static final Form SUPER_NAMEKIAN = add(Form.builder("super_namekian").parent("base", 2)
            .races(Race.NAMEKIAN).multiplier(2.4).drain(1.6, 0).colors(0x8CFF7A, -1, -1).unlock(300, 0).build());
    public static final Form ORANGE_NAMEKIAN = add(Form.builder("orange_namekian").parent("super_namekian", 3)
            .races(Race.NAMEKIAN).only(Variant.WARRIOR_CLAN).multiplier(4.0).drain(2.5, 0).scale(1.3f).colors(0xFF9A3C, -1, 0xFFB000).unlock(900, 50).build());

    // --- Frost Demon (base is the suppressed first form) ---
    public static final Form SECOND_FORM = add(Form.builder("second_form").parent("base", 1)
            .races(Race.FROST_DEMON).except(Variant.METAL).multiplier(1.6).drain(0.6, 0).scale(1.15f).colors(0xE6C8FF, -1, -1).unlock(50, 0).build());
    public static final Form THIRD_FORM = add(Form.builder("third_form").parent("second_form", 2)
            .races(Race.FROST_DEMON).except(Variant.METAL).multiplier(2.3).drain(1.0, 0).colors(0xD8B0FF, -1, -1).unlock(250, 20).build());
    public static final Form FINAL_FORM = add(Form.builder("final_form").parent("third_form", 3)
            .races(Race.FROST_DEMON).except(Variant.METAL).multiplier(3.2).drain(1.0, 0).colors(0xC890FF, -1, 0xFF3050).unlock(500, 30).build());
    public static final Form GOLDEN_FORM = add(Form.builder("golden_form").parent("final_form", 4)
            .races(Race.FROST_DEMON).only(Variant.FROST_DEMON).multiplier(5.0).drain(3.0, 0).allowsOverdrive()
            .colors(0xFFD23C, -1, 0xFF3050).unlock(1100, 50).build());

    // --- Majin ---
    public static final Form EVIL_MAJIN = add(Form.builder("evil_majin").parent("base", 1)
            .races(Race.MAJIN).multiplier(1.8).drain(1.2, 0).colors(0xC05080, -1, 0xFF2040).unlock(120, 0).build());
    public static final Form SUPER_MAJIN = add(Form.builder("super_majin").parent("evil_majin", 2)
            .races(Race.MAJIN).multiplier(2.8).drain(1.8, 0).colors(0xFF70B0, -1, 0xFF2040).unlock(300, 30).build());
    public static final Form PURE_MAJIN = add(Form.builder("pure_majin").parent("super_majin", 3)
            .races(Race.MAJIN).only(Variant.MAJIN).multipliers(4.5, 4.0, 4.0).drain(2.5, 1.0).speedBonus(0.15)
            .colors(0xFF4FA0, -1, 0x000000).unlock(900, 50).build());

    // --- Android (upgrades rather than transformations) ---
    public static final Form UPGRADE_MK2 = add(Form.builder("upgrade_mk2").parent("base", 1)
            .races(Race.ANDROID).multiplier(1.6).drain(0.5, 0).colors(0x9AD0FF, -1, 0x40C0FF).unlock(100, 0).build());
    public static final Form UPGRADE_MK3 = add(Form.builder("upgrade_mk3").parent("upgrade_mk2", 2)
            .races(Race.ANDROID).multiplier(2.4).drain(0.8, 0).colors(0x70B8FF, -1, 0x40C0FF).unlock(300, 25).build());
    public static final Form SUPER_ANDROID = add(Form.builder("super_android").parent("upgrade_mk3", 3)
            .races(Race.ANDROID).multiplier(3.6).drain(1.2, 0).colors(0xFF6060, -1, 0xFF2020).unlock(900, 50).build());

    // --- Cyborg ---
    public static final Form OVERCLOCK = add(Form.builder("overclock").parent("base", 1)
            .races(Race.CYBORG).multiplier(1.5).drain(0.8, 0).colors(0xFFC060, -1, 0xFF9020).unlock(80, 0).build());
    public static final Form FULL_CONVERSION = add(Form.builder("full_conversion").parent("overclock", 2)
            .races(Race.CYBORG).multiplier(2.6).drain(1.2, 0).colors(0xC0C8D0, -1, 0xFF2020).unlock(300, 30).build());
    public static final Form MACHINE_MUTANT = add(Form.builder("machine_mutant").parent("full_conversion", 3)
            .races(Race.CYBORG).multiplier(3.5).drain(1.8, 0).colors(0x60FFB0, -1, 0x60FFB0).unlock(900, 50).build());

    // --- Late tiers (balance pass): every race gets a level-1500 form so no line tops out a thousand levels early ---
    public static final Form TRANSCENDENT = add(Form.builder("transcendent").parent("potential_unleashed", 4)
            .races(Race.HUMAN).only(Variant.HUMAN).multiplier(5.0).drain(2.0, 0).colors(0xE0F0FF, -1, 0xC0E8FF).calmAura().unlock(1500, 50).build());
    public static final Form DRAGON_CLAN = add(Form.builder("dragon_clan").parent("dragon_sage", 4)
            .races(Race.NAMEKIAN).only(Variant.DRAGON_CLAN).multiplier(5.0).drain(2.5, 0).colors(0x40FFB0, -1, 0x40FFB0).unlock(1500, 50).build());
    public static final Form PRIMORDIAL_MAJIN = add(Form.builder("primordial_majin").parent("pure_majin", 4)
            .races(Race.MAJIN).only(Variant.MAJIN).multipliers(5.4, 4.8, 4.8).drain(3.0, 1.0).colors(0xFF2080, -1, 0xFF2040).unlock(1500, 50).build());
    public static final Form INFINITE_CORE = add(Form.builder("infinite_core").parent("super_android", 4)
            .races(Race.ANDROID).multiplier(4.8).drain(1.8, 0).colors(0x60E0FF, -1, 0x00FFFF).unlock(1500, 50).build());
    public static final Form OMEGA_FRAME = add(Form.builder("omega_frame").parent("machine_mutant", 4)
            .races(Race.CYBORG).multiplier(4.8).drain(2.4, 0).colors(0xFF6040, -1, 0xFF3010).unlock(1500, 50).build());

    // ================================================================== content expansion: variant lines (DBV tiers, FormScale)
    // Milestones on this mod's level curve: tier 1 at 150, tier 2 at 450-500, tier 3 at 900, god tiers at 1200-1800.

    private static final int LEGEND_AURA = 0x7CFF4A, LEGEND_HAIR = 0xC8FF6A, BLANK_EYES = 0xF0FFF0;
    private static final int SSJ4_AURA = 0xFF3A2A, SSJ4_HAIR = 0x1A1010, SSJ4_EYES = 0xFFD040;

    // --- Legendary Saiyan: wrath that rises the longer the fight goes on ---
    public static final Form WRATHFUL = add(Form.builder("wrathful").parent("base", 1).races(Race.SAIYAN).only(Variant.LEGENDARY)
            .dbv(1.5, 10).rising(0.2).drain(1.6, 0.4).colors(LEGEND_AURA, -1, BLANK_EYES).unlock(150, 0).build());
    public static final Form LSSJ_C_TYPE = add(Form.builder("lssj_c_type").parent("wrathful", 2).races(Race.SAIYAN).only(Variant.LEGENDARY)
            .dbv(12, 20).rising(0.2).drain(2.6, 0.6).colors(LEGEND_AURA, LEGEND_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY).scale(1.08f)
            .unlock(500, 40).build());
    public static final Form LSSJ_FULL_POWER = add(Form.builder("lssj_full_power").parent("lssj_c_type", 3).races(Race.SAIYAN).only(Variant.LEGENDARY)
            .dbv(25, 40).rising(0.2).drain(4.0, 1.0).lightning().colors(LEGEND_AURA, LEGEND_HAIR, BLANK_EYES).hair(Form.HairStyle.SPIKY_TALL)
            .scale(1.18f).unlock(900, 50).build());
    public static final Form LSSJ_CONTROLLED = add(Form.builder("lssj_controlled").parent("lssj_full_power", 4).races(Race.SAIYAN).only(Variant.LEGENDARY)
            .dbv(50).rising(0.2).drain(2.0, 0).lightning().colors(LEGEND_AURA, LEGEND_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY_TALL)
            .scale(1.1f).allowsOverdrive().unlock(1500, 60).build());

    // --- Primal Saiyan (and the Half-Saiyan Awakened Evolution path): the Golden Ape and the Super Saiyan 4 line ---
    public static final Form GOLDEN_APE = add(Form.builder("golden_ape").parent("super_saiyan_3", 4).races(Race.SAIYAN, Race.HALF_SAIYAN)
            .only(Variant.PRIMAL, Variant.AWAKENED_EVOLUTION).dbv(18).drain(2.5, 0).colors(0xFFC23C, -1, 0xFF2020).scale(3.0f)
            .noTechniques().noFlight().unlock(1000, 40).build());
    public static final Form SUPER_SAIYAN_4 = add(Form.builder("super_saiyan_4").parent("golden_ape", 5).races(Race.SAIYAN, Race.HALF_SAIYAN)
            .only(Variant.PRIMAL, Variant.AWAKENED_EVOLUTION).dbv(20, 40).drain(2.2, 0).colors(SSJ4_AURA, SSJ4_HAIR, SSJ4_EYES)
            .hair(Form.HairStyle.SPIKY_TALL).unlock(1200, 40).build());
    public static final Form SSJ4_FULL_POWER = add(Form.builder("ssj4_full_power").parent("super_saiyan_4", 6).races(Race.SAIYAN, Race.HALF_SAIYAN)
            .only(Variant.PRIMAL, Variant.AWAKENED_EVOLUTION).dbv(32, 50).drain(3.0, 0.5).lightning().colors(0xFF5A3A, SSJ4_HAIR, SSJ4_EYES)
            .hair(Form.HairStyle.SPIKY_TALL).unlock(1500, 50).build());
    public static final Form SSJ4_LIMIT_BREAKER = add(Form.builder("ssj4_limit_breaker").parent("ssj4_full_power", 7).races(Race.SAIYAN, Race.HALF_SAIYAN)
            .only(Variant.PRIMAL, Variant.AWAKENED_EVOLUTION).dbv(56).drain(3.0, 0).lightning().allowsOverdrive()
            .colors(0xFF8AE0, 0xE8E8F0, 0xFFD040).calmAura().hair(Form.HairStyle.SPIKY_TALL).unlock(1800, 60).requiresFlag("god_ki").build());

    // --- Legendary Primal Saiyan ---
    public static final Form LSSJ = add(Form.builder("lssj").parent("base", 1).races(Race.SAIYAN).only(Variant.LEGENDARY_PRIMAL)
            .dbv(6, 9).rising(0.1).drain(2.0, 0.4).colors(LEGEND_AURA, LEGEND_HAIR, BLANK_EYES).hair(Form.HairStyle.SPIKY).scale(1.08f)
            .unlock(300, 0).build());
    public static final Form LSSJ2 = add(Form.builder("lssj2").parent("lssj", 2).races(Race.SAIYAN).only(Variant.LEGENDARY_PRIMAL)
            .dbv(9, 12).rising(0.1).drain(2.8, 0.6).lightning().colors(LEGEND_AURA, LEGEND_HAIR, BLANK_EYES).hair(Form.HairStyle.SPIKY_TALL)
            .scale(1.12f).unlock(600, 40).build());
    public static final Form LSSJ3 = add(Form.builder("lssj3").parent("lssj2", 3).races(Race.SAIYAN).only(Variant.LEGENDARY_PRIMAL)
            .dbv(18).rising(0.1).drain(4.5, 1.0).lightning().colors(LEGEND_AURA, LEGEND_HAIR, BLANK_EYES).hair(Form.HairStyle.LONG)
            .scale(1.15f).unlock(900, 50).build());
    public static final Form LEGENDARY_GREAT_APE = add(Form.builder("legendary_great_ape").parent("lssj3", 4).races(Race.SAIYAN)
            .only(Variant.LEGENDARY_PRIMAL).dbv(20).drain(2.5, 0).colors(0x6AFF3A, -1, 0xFF2020).scale(3.2f).noTechniques().noFlight()
            .unlock(1100, 40).build());
    public static final Form LSSJ4 = add(Form.builder("lssj4").parent("legendary_great_ape", 5).races(Race.SAIYAN).only(Variant.LEGENDARY_PRIMAL)
            .dbv(22, 44).rising(0.1).drain(2.4, 0).colors(SSJ4_AURA, SSJ4_HAIR, SSJ4_EYES).hair(Form.HairStyle.SPIKY_TALL).scale(1.12f)
            .unlock(1300, 40).build());
    public static final Form LSSJ4_FULL_POWER = add(Form.builder("lssj4_full_power").parent("lssj4", 6).races(Race.SAIYAN).only(Variant.LEGENDARY_PRIMAL)
            .dbv(34, 52).rising(0.1).drain(3.2, 0.5).lightning().colors(0xFF5A3A, SSJ4_HAIR, SSJ4_EYES).hair(Form.HairStyle.SPIKY_TALL)
            .scale(1.15f).unlock(1600, 50).build());
    public static final Form LSSJ4_LIMIT_BREAKER = add(Form.builder("lssj4_limit_breaker").parent("lssj4_full_power", 7).races(Race.SAIYAN)
            .only(Variant.LEGENDARY_PRIMAL).dbv(60).drain(3.0, 0).lightning().allowsOverdrive().colors(0xFF8AE0, 0xE8E8F0, 0xFFD040).calmAura()
            .hair(Form.HairStyle.SPIKY_TALL).scale(1.15f).unlock(1900, 60).requiresFlag("god_ki").build());

    // --- Half-Saiyan paths ---
    public static final Form SUPER_SAIYAN_RAGE = add(Form.builder("super_saiyan_rage").parent("super_saiyan_god", 5).races(Race.HALF_SAIYAN)
            .only(Variant.FUTURE_LINEAGE).dbv(32, 56).drain(3.2, 0).lightning().allowsOverdrive().colors(0x9AD8FF, 0xC8E8FF, 0x2E7DFF)
            .hair(Form.HairStyle.SPIKY_TALL).unlock(1600, 50).build());
    public static final Form BEAST_AWAKENING = add(Form.builder("beast_awakening").parent("ultimate", 5).races(Race.HALF_SAIYAN)
            .only(Variant.NEW_GENERATION).dbv(56).drain(2.8, 0.5).lightning().allowsOverdrive().colors(0xD070FF, 0xF2F2F8, 0xFF3050)
            .hair(Form.HairStyle.SPIKY_TALL).scale(1.05f).unlock(1700, 50).build());

    // --- Human paths ---
    public static final Form SURGE = add(Form.builder("surge").parent("full_power", 2).races(Race.HUMAN).only(Variant.ANCIENT_HERMIT)
            .dbv(2, 8).drain(0.8, 0).colors(0xF0E6C8, -1, -1).calmAura().unlock(150, 0).build());
    public static final Form SURGE_OVERFLOW = add(Form.builder("surge_overflow").parent("surge", 3).races(Race.HUMAN).only(Variant.ANCIENT_HERMIT)
            .dbv(10, 16).drain(1.2, 0).colors(0xF8EAC0, -1, 0xFFE070).calmAura().unlock(700, 40).build());
    public static final Form GODLY_SURGE = add(Form.builder("godly_surge").parent("surge_overflow", 4).races(Race.HUMAN).only(Variant.ANCIENT_HERMIT)
            .dbv(22, 56).drain(1.6, 0).colors(0xFFF4D0, -1, 0xFFE070).calmAura().unlock(1300, 40).requiresFlag("god_ki").build());
    public static final Form NO_EGO_ZONE = add(Form.builder("no_ego_zone").parent("buffed", 3).races(Race.HUMAN).only(Variant.PEAK_HUMAN)
            .dbv(12, 18).drain(1.4, 0.4).colors(0xE8F4FF, -1, 0x9FD8FF).unlock(700, 40).build());
    public static final Form GODLY_EGO_ZONE = add(Form.builder("godly_ego_zone").parent("no_ego_zone", 4).races(Race.HUMAN).only(Variant.PEAK_HUMAN)
            .dbv(22, 56).drain(1.2, 0).colors(0xC8E0FF, -1, 0x6AB8FF).calmAura().unlock(1300, 40).requiresFlag("god_ki").build());
    public static final Form INNER_EYE = add(Form.builder("inner_eye").parent("full_power", 2).races(Race.HUMAN).only(Variant.TRICLOPS)
            .dbv(2).drain(0.6, 0).colors(0xD8C8FF, -1, 0xB090FF).unlock(150, 0).build());
    public static final Form AWAKENED_EYE = add(Form.builder("awakened_eye").parent("inner_eye", 3).races(Race.HUMAN).only(Variant.TRICLOPS)
            .dbv(4, 25).drain(1.2, 0).colors(0xC0A8FF, -1, 0x9070FF).unlock(500, 30).build());
    public static final Form GODLY_EYE = add(Form.builder("godly_eye").parent("awakened_eye", 4).races(Race.HUMAN).only(Variant.TRICLOPS)
            .dbv(18, 56).drain(0, 0).colors(0xE8D8FF, -1, 0xD0B8FF).calmAura().unlock(1100, 40).requiresFlag("god_ki").build());

    // --- Namekian clans ---
    public static final Form NAMEKIAN_WARLORD = add(Form.builder("namekian_warlord").parent("orange_namekian", 4).races(Race.NAMEKIAN)
            .only(Variant.WARRIOR_CLAN).dbv(56).drain(2.6, 0.5).scale(1.2f).lightning().colors(0xFFB050, -1, 0xFF8000).unlock(1700, 50).build());
    public static final Form DRAGON_SAGE = add(Form.builder("dragon_sage").parent("super_namekian", 3).races(Race.NAMEKIAN)
            .only(Variant.DRAGON_CLAN).dbv(8, 16).drain(1.4, 0).calmAura().colors(0xE8FFF0, -1, 0x80FFC0).unlock(700, 40).build());
    public static final Form DEMON_NAMEKIAN = add(Form.builder("demon_namekian").parent("super_namekian", 3).races(Race.NAMEKIAN)
            .only(Variant.DEMON_CLAN).dbv(12).drain(1.8, 0.4).colors(0xB070FF, -1, 0xFF2040).unlock(700, 40).build());
    public static final Form DEMON_KING = add(Form.builder("demon_king").parent("demon_namekian", 4).races(Race.NAMEKIAN)
            .only(Variant.DEMON_CLAN).dbv(24, 44).drain(2.4, 0.4).lightning().scale(1.1f).colors(0x8A40E0, -1, 0xFF1030).unlock(1500, 50).build());

    // --- Frost Demon: Mutant overlords and the Metal line ---
    public static final Form MUTANT_OVERLORD = add(Form.builder("mutant_overlord").parent("final_form", 4).races(Race.FROST_DEMON)
            .only(Variant.MUTANT).dbv(14).drain(2.2, 0.4).colors(0xFF5AC8, -1, 0xFF1060).scale(1.1f).unlock(700, 40).build());
    public static final Form MUTANT_GOD = add(Form.builder("mutant_god").parent("mutant_overlord", 5).races(Race.FROST_DEMON)
            .only(Variant.MUTANT).dbv(30, 44).drain(2.4, 0).lightning().calmAura().colors(0xC040FF, -1, 0xFF1060).scale(1.1f)
            .unlock(1500, 50).build());
    public static final Form METAL_SHELL = add(Form.builder("metal_shell").parent("base", 1).races(Race.FROST_DEMON).only(Variant.METAL)
            .dbv(3).drain(0.6, 0).colors(0xC8D8E8, -1, 0xFF3050).unlock(100, 0).build());
    public static final Form METAL_OVERCLOCK = add(Form.builder("metal_overclock").parent("metal_shell", 2).races(Race.FROST_DEMON).only(Variant.METAL)
            .dbv(8, 14).drain(1.4, 0).lightning().colors(0xA8C0E0, -1, 0xFF3050).unlock(500, 30).build());
    public static final Form METAL_GOD_CORE = add(Form.builder("metal_god_core").parent("metal_overclock", 3).races(Race.FROST_DEMON).only(Variant.METAL)
            .dbv(22, 40).drain(2.0, 0).lightning().allowsOverdrive().colors(0xE8F0FF, -1, 0x40E0FF).calmAura().unlock(1300, 50).build());

    // --- Corrupted Majin: the Pure form that eats its own sanity ---
    public static final Form PURE_CORRUPTION = add(Form.builder("pure_corruption").parent("super_majin", 3).races(Race.MAJIN)
            .only(Variant.CORRUPTED).dbv(12, 56).drain(3.0, 1.0).lightning().speedBonus(0.15).colors(0x9A70C0, -1, 0xFF2040).unlock(900, 40).build());

    // --- Vampire ---
    public static final Form THIRST = add(Form.builder("thirst").parent("base", 1).races(Race.VAMPIRE)
            .dbv(1.25).drain(0.4, 0).colors(0xA01028, -1, 0xFF1020).unlock(100, 0).build());
    public static final Form BLOOD_RUSH = add(Form.builder("blood_rush").parent("thirst", 2).races(Race.VAMPIRE)
            .dbv(2, 4).drain(0.8, 0.2).speedBonus(0.1).colors(0xC01030, -1, 0xFF1020).unlock(150, 0).build());
    public static final Form NIGHTBORN = add(Form.builder("nightborn").parent("blood_rush", 2).races(Race.VAMPIRE)
            .dbv(5, 8).drain(1.4, 0.4).speedBonus(0.15).colors(0x8A0A20, -1, 0xFF1020).unlock(450, 30).build());
    public static final Form ELDER_BLOOD = add(Form.builder("elder_blood").parent("nightborn", 3).races(Race.VAMPIRE)
            .dbv(16).drain(2.4, 0.6).lightning().colors(0x600818, 0xE8E8F0, 0xFF1020).unlock(900, 40).build());
    public static final Form CRIMSON_SOVEREIGN = add(Form.builder("crimson_sovereign").parent("elder_blood", 4).races(Race.VAMPIRE)
            .dbv(22, 32).drain(2.0, 0).calmAura().colors(0xFF1838, 0xF8F0F0, 0xFF0020).unlock(1500, 40).requiresFlag("god_ki").build());
    public static final Form BLOOD_MOON_MONARCH = add(Form.builder("blood_moon_monarch").parent("crimson_sovereign", 5).races(Race.VAMPIRE)
            .dbv(56).drain(2.8, 0).lightning().allowsOverdrive().colors(0xFF0030, 0xFFE0E8, 0xFFD040).scale(1.08f).unlock(1800, 50).build());

    // --- Bio-Android: evolving towards perfection ---
    public static final Form CELL_SURGE = add(Form.builder("cell_surge").parent("base", 1).races(Race.BIO_ANDROID)
            .dbv(1.25).drain(0.4, 0).colors(0x7ADA60, -1, 0xFF3060).unlock(100, 0).build());
    public static final Form SEMI_PERFECT = add(Form.builder("semi_perfect").parent("cell_surge", 2).races(Race.BIO_ANDROID)
            .dbv(2, 4).drain(0.8, 0).colors(0x9AFF70, -1, 0xFF3060).scale(1.05f).unlock(150, 0).build());
    public static final Form PERFECT = add(Form.builder("perfect").parent("semi_perfect", 2).races(Race.BIO_ANDROID)
            .dbv(5, 8).drain(1.2, 0).colors(0x70FF90, -1, 0xFF3060).unlock(450, 30).build());
    public static final Form SUPER_PERFECT = add(Form.builder("super_perfect").parent("perfect", 3).races(Race.BIO_ANDROID)
            .dbv(16, 22).drain(2.0, 0.4).lightning().colors(0xC0FF70, -1, 0xFF3060).unlock(900, 40).build());
    public static final Form ULTIMATE_PERFECT = add(Form.builder("ultimate_perfect").parent("super_perfect", 4).races(Race.BIO_ANDROID)
            .dbv(32).drain(2.2, 0).calmAura().colors(0xE8FFC0, -1, 0xFF3060).unlock(1500, 40).build());
    public static final Form ZENITH_PERFECT = add(Form.builder("zenith_perfect").parent("ultimate_perfect", 5).races(Race.BIO_ANDROID)
            .dbv(56).drain(2.8, 0).lightning().allowsOverdrive().colors(0x60FFE0, -1, 0xFFD040).unlock(1800, 50).requiresFlag("god_ki").build());

    // --- Tuffle: science and revenge ---
    public static final Form FOCUS_PROTOCOL = add(Form.builder("focus_protocol").parent("base", 1).races(Race.TUFFLE)
            .dbv(1.25).drain(0.4, 0).colors(0xC8F0A0, -1, 0x80FF40).unlock(100, 0).build());
    public static final Form NEURAL_OVERCLOCK = add(Form.builder("neural_overclock").parent("focus_protocol", 2).races(Race.TUFFLE)
            .dbv(2, 4).drain(0.8, 0).colors(0xD0FF90, -1, 0x80FF40).unlock(150, 0).build());
    public static final Form MACHINE_ASCENDANT = add(Form.builder("machine_ascendant").parent("neural_overclock", 2).races(Race.TUFFLE)
            .dbv(5, 8).drain(1.2, 0).colors(0xA0E870, -1, 0xFF4040).unlock(450, 30).build());
    public static final Form REVENGE_ENGINE = add(Form.builder("revenge_engine").parent("machine_ascendant", 3).races(Race.TUFFLE)
            .dbv(16).drain(2.2, 0.5).lightning().colors(0xFF6040, -1, 0xFF2020).unlock(900, 40).build());
    public static final Form TUFFLE_KING = add(Form.builder("tuffle_king").parent("revenge_engine", 4).races(Race.TUFFLE)
            .dbv(22, 32).drain(2.0, 0).calmAura().colors(0xFFE08A, -1, 0xFFD040).unlock(1500, 40).build());
    public static final Form GOLDEN_TUFFLE = add(Form.builder("golden_tuffle").parent("tuffle_king", 5).races(Race.TUFFLE)
            .dbv(56).drain(2.8, 0).lightning().allowsOverdrive().colors(0xFFC23C, 0xF0F0F0, 0xFF2020).unlock(1800, 50).requiresFlag("god_ki").build());

    // --- Gen Alien: mutation without limit ---
    public static final Form ADRENAL_RUSH = add(Form.builder("adrenal_rush").parent("base", 1).races(Race.GEN_ALIEN)
            .dbv(1.25).drain(0.4, 0).colors(0x9AD8F0, -1, 0x40FFC0).unlock(100, 0).build());
    public static final Form MUTATION_ALPHA = add(Form.builder("mutation_alpha").parent("adrenal_rush", 2).races(Race.GEN_ALIEN)
            .dbv(2, 4).drain(0.8, 0).colors(0x8AE0FF, -1, 0x40FFC0).unlock(150, 0).build());
    public static final Form MUTATION_BETA = add(Form.builder("mutation_beta").parent("mutation_alpha", 2).races(Race.GEN_ALIEN)
            .dbv(5, 8).drain(1.2, 0).scale(1.05f).colors(0x6AC8FF, -1, 0x40FFC0).unlock(450, 30).build());
    public static final Form BERSERKER = add(Form.builder("berserker").parent("mutation_beta", 3).races(Race.GEN_ALIEN)
            .dbv(16).drain(2.4, 0.8).scale(1.2f).speedBonus(-0.1).lightning().colors(0xFF8A3A, -1, 0xFF2020).unlock(900, 40).build());
    public static final Form APEX_MUTATION = add(Form.builder("apex_mutation").parent("berserker", 4).races(Race.GEN_ALIEN)
            .dbv(22, 32).drain(2.0, 0).scale(1.1f).colors(0xFFD0FF, -1, 0xC040FF).unlock(1500, 40).build());
    public static final Form COSMIC_APEX = add(Form.builder("cosmic_apex").parent("apex_mutation", 5).races(Race.GEN_ALIEN)
            .dbv(56).drain(2.8, 0).lightning().calmAura().allowsOverdrive().colors(0x9A8AFF, -1, 0xFFFFFF).unlock(1800, 50).requiresFlag("god_ki").build());

    // --- Core Person: Kai divinity and Demon darkness ---
    public static final Form SERENE_MIND = add(Form.builder("serene_mind").parent("base", 1).races(Race.CORE_PERSON).only(Variant.KAI)
            .dbv(1.25).drain(0.3, 0).calmAura().colors(0xFFF8E0, -1, -1).unlock(100, 0).build());
    public static final Form DIVINE_FOCUS = add(Form.builder("divine_focus").parent("serene_mind", 2).races(Race.CORE_PERSON).only(Variant.KAI)
            .dbv(2, 4).drain(0.6, 0).calmAura().colors(0xFFF2C8, -1, -1).unlock(150, 0).build());
    public static final Form KAI_AWAKENING = add(Form.builder("kai_awakening").parent("divine_focus", 2).races(Race.CORE_PERSON).only(Variant.KAI)
            .dbv(5, 8).drain(1.0, 0).calmAura().colors(0xFFE8A0, -1, 0x80C0FF).unlock(450, 30).build());
    public static final Form SUPREME_KAI = add(Form.builder("supreme_kai").parent("kai_awakening", 3).races(Race.CORE_PERSON).only(Variant.KAI)
            .dbv(22).drain(1.4, 0).calmAura().colors(0xFFF8E0, -1, 0x60B0FF).unlock(1000, 40).build());
    public static final Form GRAND_KAI_MANTLE = add(Form.builder("grand_kai_mantle").parent("supreme_kai", 4).races(Race.CORE_PERSON).only(Variant.KAI)
            .dbv(32, 56).drain(1.8, 0).calmAura().allowsOverdrive().colors(0xFFFFFF, -1, 0x40A0FF).unlock(1500, 50).requiresFlag("god_ki").build());
    public static final Form DEMON_BLOOD = add(Form.builder("demon_blood").parent("base", 1).races(Race.CORE_PERSON).only(Variant.DEMON)
            .dbv(1.25).drain(0.4, 0).colors(0xD04040, -1, 0xFFD040).unlock(100, 0).build());
    public static final Form DEMON_MARK = add(Form.builder("demon_mark").parent("demon_blood", 2).races(Race.CORE_PERSON).only(Variant.DEMON)
            .dbv(2, 4).drain(0.8, 0).colors(0xFF5050, -1, 0xFFD040).unlock(150, 0).build());
    public static final Form DARK_EVOLUTION = add(Form.builder("dark_evolution").parent("demon_mark", 2).races(Race.CORE_PERSON).only(Variant.DEMON)
            .dbv(5, 8).drain(1.4, 0.3).colors(0xD02040, -1, 0xFFD040).unlock(450, 30).build());
    public static final Form DEMON_LORD = add(Form.builder("demon_lord").parent("dark_evolution", 3).races(Race.CORE_PERSON).only(Variant.DEMON)
            .dbv(22).drain(2.0, 0.4).lightning().scale(1.1f).colors(0xA01030, -1, 0xFF8000).unlock(1000, 40).build());
    public static final Form DEMON_GOD = add(Form.builder("demon_god").parent("demon_lord", 4).races(Race.CORE_PERSON).only(Variant.DEMON)
            .dbv(32, 56).drain(2.4, 0).lightning().allowsOverdrive().colors(0x6A0A20, -1, 0xFF4000).unlock(1500, 50).requiresFlag("god_ki").build());

    private Forms() {}

    private static Form add(Form f) {
        BY_ID.put(f.id(), f);
        return f;
    }

    public static Form byId(String id) {
        return BY_ID.getOrDefault(id, BASE);
    }

    public static boolean exists(String id) {
        return BY_ID.containsKey(id);
    }

    public static List<Form> all() {
        return List.copyOf(BY_ID.values());
    }

    /** Forms a race can use (excluding base), in definition order. */
    public static List<Form> forRace(Race race) {
        List<Form> out = new ArrayList<>();
        for (Form f : BY_ID.values()) if (!f.isBase() && f.races().contains(race)) out.add(f);
        return out;
    }

    /** Direct children of {@code parentId} for {@code race}. */
    public static List<Form> children(String parentId, Race race) {
        List<Form> out = new ArrayList<>();
        for (Form f : forRace(race)) if (parentId.equals(f.parent())) out.add(f);
        return out;
    }

    /** Forms a character of this race and variant can use (excluding base), in definition order. */
    public static List<Form> forCharacter(Race race, com.dbzenith.race.Variant variant) {
        List<Form> out = new ArrayList<>();
        for (Form f : forRace(race)) if (f.allows(variant)) out.add(f);
        return out;
    }

    /** Direct children of {@code parentId} open to this race and variant. */
    public static List<Form> children(String parentId, Race race, com.dbzenith.race.Variant variant) {
        List<Form> out = new ArrayList<>();
        for (Form f : forCharacter(race, variant)) if (parentId.equals(f.parent())) out.add(f);
        return out;
    }

    /** True if {@code ancestorId} is on the parent chain of {@code form} (or is the form itself). */
    public static boolean isOnPath(String ancestorId, Form form) {
        for (Form f = form; f != null; f = f.isBase() ? null : byId(f.parent())) {
            if (f.id().equals(ancestorId)) return true;
        }
        return false;
    }
}
