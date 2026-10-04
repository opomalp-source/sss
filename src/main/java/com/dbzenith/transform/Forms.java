package com.dbzenith.transform;

import com.dbzenith.data.PlayerData;
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
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multiplier(2.0).drain(1.5, 0)
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY).unlock(150, 0).build());

    public static final Form SUPER_SAIYAN_G2 = add(Form.builder("super_saiyan_g2").parent("super_saiyan", 2)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multipliers(2.4, 2.1, 2.25).drain(2.2, 0)
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY).unlock(250, 25).build());

    public static final Form SUPER_SAIYAN_G3 = add(Form.builder("super_saiyan_g3").parent("super_saiyan_g2", 3)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multipliers(3.0, 1.6, 2.4).drain(3.5, 2.0).speedBonus(-0.3)
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY_TALL).unlock(320, 30).build());

    public static final Form SUPER_SAIYAN_2 = add(Form.builder("super_saiyan_2").parent("super_saiyan", 2)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multiplier(3.0).drain(2.5, 0).lightning()
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.SPIKY_TALL).unlock(500, 50).build());

    public static final Form SUPER_SAIYAN_3 = add(Form.builder("super_saiyan_3").parent("super_saiyan_2", 3)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multiplier(4.0).drain(5.0, 1.0).lightning()
            .colors(GOLD_AURA, GOLD_HAIR, TEAL_EYES).hair(Form.HairStyle.LONG).unlock(900, 50).build());

    public static final Form SUPER_SAIYAN_GOD = add(Form.builder("super_saiyan_god").parent("base", 4)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multiplier(5.0).drain(1.0, 0)
            .colors(0xFF3B3B, 0xC8283C, 0xD0263E).hair(Form.HairStyle.SLIM).unlock(1200, 0).requiresFlag("god_ki").build());

    public static final Form SUPER_SAIYAN_BLUE = add(Form.builder("super_saiyan_blue").parent("super_saiyan_god", 5)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multiplier(6.0).drain(3.0, 0).allowsOverdrive()
            .colors(0x36B8FF, 0x4FC3FF, 0x2E7DFF).hair(Form.HairStyle.SPIKY).unlock(1800, 50).build());

    public static final Form GREAT_APE = add(Form.builder("great_ape").parent("base", 1)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multipliers(4.0, 0.6, 2.0).drain(0, 0)
            .colors(0xB0402A, -1, 0xFF2020).scale(3.0f).noTechniques().noFlight().trigger(Form.Trigger.MOON).build());

    // --- Half-Saiyan exclusive (also uses the Saiyan line above) ---
    public static final Form ULTIMATE = add(Form.builder("ultimate").parent("base", 4)
            .races(Race.HALF_SAIYAN).multiplier(4.5).drain(0.6, 0)
            .colors(0xFFFFFF, -1, -1).unlock(1000, 0).requiresFlag("potential_unlocked").build());

    // --- Human ---
    public static final Form FULL_POWER = add(Form.builder("full_power").parent("base", 1)
            .races(Race.HUMAN).multiplier(1.5).drain(0.8, 0).colors(0xF2F6FF, -1, -1).unlock(100, 0).build());
    public static final Form BUFFED = add(Form.builder("buffed").parent("full_power", 2)
            .races(Race.HUMAN).multipliers(2.8, 1.8, 2.4).drain(1.5, 1.0).speedBonus(-0.15)
            .colors(0xFFE8C0, -1, -1).unlock(300, 30).build());
    public static final Form POTENTIAL_UNLEASHED = add(Form.builder("potential_unleashed").parent("full_power", 3)
            .races(Race.HUMAN).multiplier(3.4).drain(1.0, 0).colors(0xFFFFFF, -1, 0x9FD8FF).unlock(700, 50).build());

    // --- Namekian ---
    public static final Form GIANT_NAMEKIAN = add(Form.builder("giant_namekian").parent("base", 1)
            .races(Race.NAMEKIAN).multipliers(2.5, 0.8, 1.5).drain(2.0, 0).scale(2.5f)
            .colors(0xB8FFB0, -1, -1).unlock(150, 0).build());
    public static final Form SUPER_NAMEKIAN = add(Form.builder("super_namekian").parent("base", 2)
            .races(Race.NAMEKIAN).multiplier(2.4).drain(1.6, 0).colors(0x8CFF7A, -1, -1).unlock(300, 0).build());
    public static final Form ORANGE_NAMEKIAN = add(Form.builder("orange_namekian").parent("super_namekian", 3)
            .races(Race.NAMEKIAN).multiplier(4.0).drain(2.5, 0).scale(1.3f).colors(0xFF9A3C, -1, 0xFFB000).unlock(900, 50).build());

    // --- Frost Demon (base is the suppressed first form) ---
    public static final Form SECOND_FORM = add(Form.builder("second_form").parent("base", 1)
            .races(Race.FROST_DEMON).multiplier(1.6).drain(0.6, 0).scale(1.15f).colors(0xE6C8FF, -1, -1).unlock(50, 0).build());
    public static final Form THIRD_FORM = add(Form.builder("third_form").parent("second_form", 2)
            .races(Race.FROST_DEMON).multiplier(2.3).drain(1.0, 0).colors(0xD8B0FF, -1, -1).unlock(250, 20).build());
    public static final Form FINAL_FORM = add(Form.builder("final_form").parent("third_form", 3)
            .races(Race.FROST_DEMON).multiplier(3.2).drain(1.0, 0).colors(0xC890FF, -1, 0xFF3050).unlock(500, 30).build());
    public static final Form GOLDEN_FORM = add(Form.builder("golden_form").parent("final_form", 4)
            .races(Race.FROST_DEMON).multiplier(5.0).drain(3.0, 0).allowsOverdrive()
            .colors(0xFFD23C, -1, 0xFF3050).unlock(1100, 50).build());

    // --- Majin ---
    public static final Form EVIL_MAJIN = add(Form.builder("evil_majin").parent("base", 1)
            .races(Race.MAJIN).multiplier(1.8).drain(1.2, 0).colors(0xC05080, -1, 0xFF2040).unlock(120, 0).build());
    public static final Form SUPER_MAJIN = add(Form.builder("super_majin").parent("evil_majin", 2)
            .races(Race.MAJIN).multiplier(2.8).drain(1.8, 0).colors(0xFF70B0, -1, 0xFF2040).unlock(300, 30).build());
    public static final Form PURE_MAJIN = add(Form.builder("pure_majin").parent("super_majin", 3)
            .races(Race.MAJIN).multipliers(4.5, 4.0, 4.0).drain(2.5, 1.0).speedBonus(0.15)
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
            .races(Race.HUMAN).multiplier(5.0).drain(2.0, 0).colors(0xE0F0FF, -1, 0xC0E8FF).unlock(1500, 50).build());
    public static final Form DRAGON_CLAN = add(Form.builder("dragon_clan").parent("orange_namekian", 4)
            .races(Race.NAMEKIAN).multiplier(5.0).drain(2.5, 0).colors(0x40FFB0, -1, 0x40FFB0).unlock(1500, 50).build());
    public static final Form PRIMORDIAL_MAJIN = add(Form.builder("primordial_majin").parent("pure_majin", 4)
            .races(Race.MAJIN).multipliers(5.4, 4.8, 4.8).drain(3.0, 1.0).colors(0xFF2080, -1, 0xFF2040).unlock(1500, 50).build());
    public static final Form INFINITE_CORE = add(Form.builder("infinite_core").parent("super_android", 4)
            .races(Race.ANDROID).multiplier(4.8).drain(1.8, 0).colors(0x60E0FF, -1, 0x00FFFF).unlock(1500, 50).build());
    public static final Form OMEGA_FRAME = add(Form.builder("omega_frame").parent("machine_mutant", 4)
            .races(Race.CYBORG).multiplier(4.8).drain(2.4, 0).colors(0xFF6040, -1, 0xFF3010).unlock(1500, 50).build());

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

    /** True if {@code ancestorId} is on the parent chain of {@code form} (or is the form itself). */
    public static boolean isOnPath(String ancestorId, Form form) {
        for (Form f = form; f != null; f = f.isBase() ? null : byId(f.parent())) {
            if (f.id().equals(ancestorId)) return true;
        }
        return false;
    }
}
