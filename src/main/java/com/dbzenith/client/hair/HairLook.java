package com.dbzenith.client.hair;

import com.dbzenith.transform.Form;

/**
 * Which version of a character's hair a form shows. Every stock hairstyle is sculpted once per look, so Super Saiyan
 * Vegeta keeps his widow's peak, Super Saiyan 2 Gohan gets his single bang and Super Saiyan 3 grows a mane to the waist.
 */
public enum HairLook {
    /** The character's own hair. */
    BASE,
    /** Super Saiyan, Grade 2, Blue and the Legendary first stage: lifted, stiff and spiked. */
    SSJ,
    /** Super Saiyan 2, Grade 3, Rage and the later Legendary stages: sharper, taller, a loose bang. */
    SSJ2,
    /** Super Saiyan 3: a mane to the waist, the brow bare. */
    SSJ3,
    /** Super Saiyan 4: wild, long and dark, falling down the back. */
    SSJ4,
    /** Super Saiyan God: the own hair, slimmer and smoothed. */
    GOD,
    /** Beast: a towering crest. */
    BEAST;

    /** The look a form gives. Forms that leave the hair alone keep the base look. */
    public static HairLook of(Form form) {
        return switch (form.id()) {
            case "super_saiyan_god" -> GOD;
            case "super_saiyan_3", "lssj3" -> SSJ3;
            case "super_saiyan_4", "ssj4_full_power", "ssj4_limit_breaker", "lssj4", "lssj4_full_power", "lssj4_limit_breaker" -> SSJ4;
            case "beast_awakening" -> BEAST;
            default -> ofStyle(form.hairStyle());
        };
    }

    /** The look a bare hair style stands for (hair codes carry only the style). */
    public static HairLook ofStyle(Form.HairStyle style) {
        return switch (style) {
            case NONE -> BASE;
            case SPIKY -> SSJ;
            case SPIKY_TALL -> SSJ2;
            case LONG -> SSJ3;
            case SLIM -> GOD;
        };
    }

    /** A Super Saiyan look, whose hair stands stiff in the wind. */
    public boolean stiff() {
        return this != BASE && this != GOD;
    }
}
