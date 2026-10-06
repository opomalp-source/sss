package com.dbzenith.client.render;

/**
 * How a form reshapes the body (CX-14f), beyond its overall size: heavy forms swell the torso and limbs, slim ones
 * pare them down, Frost Demon forms change the horns and skull, and Super Saiyan 3 trades its brows for a heavy
 * ridge. Client-side looks only; hitboxes follow the form's overall scale as before.
 */
public final class FormShape {
    private FormShape() {}

    /** Bulk: 2 hulking, 1 buffed, 0 as built, negative slimmer. */
    public static float bulk(String form) {
        if (form == null) return 0;
        if (form.equals("buffed") || form.startsWith("lssj") || form.equals("berserker") || form.equals("wrathful")
                || form.equals("primordial_majin") || form.equals("mutant_overlord")) return 2;
        if (form.equals("full_power") || form.equals("super_saiyan_rage") || form.equals("beast_awakening") || form.equals("super_majin")
                || form.equals("demon_king") || form.equals("namekian_warlord") || form.equals("super_perfect") || form.equals("crimson_sovereign")
                || form.equals("ultimate_perfect") || form.equals("apex_mutation") || form.equals("revenge_engine")) return 1;
        if (form.equals("pure_majin") || form.equals("evil_majin")) return -1;
        return 0;
    }

    /** Frost Demon horn size: 1 first form, larger for the second form, 0 for the smooth-headed final forms. */
    public static float hornScale(String form) {
        if (form == null) return 1;
        return switch (form) {
            case "second_form" -> 1.45f;
            case "third_form" -> 0.6f;
            case "final_form", "full_power", "golden_form", "metal_god_core", "mutant_god", "mutant_overlord" -> 0f;
            default -> 1f;
        };
    }

    /** The third form's long, swept-back skull. */
    public static boolean crest(String form) {
        return "third_form".equals(form);
    }

    /** Super Saiyan 3: no brows, a heavy ridge over the eyes. */
    public static boolean browRidge(String form) {
        return "super_saiyan_3".equals(form);
    }
}
