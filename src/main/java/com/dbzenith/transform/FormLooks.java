package com.dbzenith.transform;

import java.util.HashMap;
import java.util.Map;

/**
 * How a transformation changes the body beyond hair, eyes and aura: race skins swapped for recoloured ones (an orange
 * Namekian, a golden Frost Demon, a grey evil Majin...), a tint for the generated bodies, and fur over the body
 * (Super Saiyan 4). The textures are painted by ArtGen.FormLooks.
 */
public final class FormLooks {
    /**
     * @param skins   race skin name -> the skin worn in this form (only skins listed change)
     * @param tint    generated-body skin colour in this form (0xRRGGBB), or -1 to keep yours
     * @param overlay a texture drawn over any body (textures/entity/form/&lt;name&gt;.png), or null
     */
    public record Look(Map<String, String> skins, int tint, String overlay) {}

    private static final Look NONE = new Look(Map.of(), -1, null);
    private static final Map<String, Look> LOOKS = new HashMap<>();

    static {
        skin("orange_namekian", "namekian", "namekian_orange", 0xF08A30);
        skin("demon_king", "demon_namekian", "demon_namekian_king", 0x7A1828);
        skin("golden_form", "frost_demon", "frost_demon_golden", 0xE8B830);
        skin("metal_god_core", "metal_frost_demon", "metal_frost_demon_core", -1);
        skin("mutant_god", "mutant_frost_demon", "mutant_frost_demon_god", -1);
        skin("evil_majin", "majin", "majin_evil", 0x9A9AA0);
        skin("super_majin", "majin", "majin_evil", 0x9A9AA0);
        skin("pure_majin", "majin", "majin_pure", -1);
        skin("primordial_majin", "majin", "majin_pure", -1);
        skin("pure_corruption", "corrupted_majin", "corrupted_majin_pure", -1);
        skin("crimson_sovereign", "vampire", "vampire_crimson", -1);
        skin("blood_moon_monarch", "vampire", "vampire_crimson", -1);
        skin("perfect", "bio_android", "bio_android_perfect", -1);
        skin("super_perfect", "bio_android", "bio_android_perfect", -1);
        skin("ultimate_perfect", "bio_android", "bio_android_perfect", -1);
        skin("zenith_perfect", "bio_android", "bio_android_zenith", 0xE0B040);
        skin("golden_tuffle", "tuffle", "tuffle_golden", 0xE8C050);
        skin("apex_mutation", "gen_alien", "gen_alien_apex", -1);
        skin("cosmic_apex", "gen_alien", "gen_alien_apex", -1);
        skin("demon_lord", "core_demon", "core_demon_god", -1);
        skin("demon_god", "core_demon", "core_demon_god", -1);
        skin("supreme_kai", "kai", "kai_supreme", -1);
        skin("grand_kai_mantle", "kai", "kai_supreme", -1);
        for (String f : new String[]{"super_saiyan_4", "ssj4_full_power", "lssj4", "lssj4_full_power"}) LOOKS.put(f, new Look(Map.of(), -1, "ssj4_fur"));
        for (String f : new String[]{"ssj4_limit_breaker", "lssj4_limit_breaker"}) LOOKS.put(f, new Look(Map.of(), -1, "ssj4_fur_silver"));
    }

    private FormLooks() {}

    private static void skin(String form, String from, String to, int tint) {
        LOOKS.put(form, new Look(Map.of(from, to), tint, null));
    }

    public static Look of(String formId) {
        return LOOKS.getOrDefault(formId, NONE);
    }

    /** The race skin worn: the form's recoloured one if it has one for this skin. */
    public static String skin(String formId, String baseSkin) {
        return baseSkin == null ? null : of(formId).skins().getOrDefault(baseSkin, baseSkin);
    }

    public static java.util.Set<String> forms() {
        return LOOKS.keySet();
    }
}
