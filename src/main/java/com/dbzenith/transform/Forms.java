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
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multiplier(6.5).drain(3.0, 0).allowsOverdrive()
            .colors(0x36B8FF, 0x4FC3FF, 0x2E7DFF).hair(Form.HairStyle.SPIKY).unlock(1800, 50).build());

    public static final Form GREAT_APE = add(Form.builder("great_ape").parent("base", 1)
            .races(Race.SAIYAN, Race.HALF_SAIYAN).multipliers(4.0, 0.6, 2.0).drain(0, 0)
            .colors(0xB0402A, -1, 0xFF2020).scale(3.0f).noTechniques().noFlight().trigger(Form.Trigger.MOON).build());

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
