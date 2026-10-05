package com.dbzenith.client.render;

import com.dbzenith.config.DBZConfig;

/** Which art the character layers draw (Settings > Style > Art style): painted (the default), HD, or the classic pixels. */
public final class ArtStyle {
    public static final int PAINTED = 0, HD = 1, CLASSIC = 2;

    private ArtStyle() {}

    public static int get() {
        try {
            return DBZConfig.CLIENT.artStyle.get();
        } catch (RuntimeException e) {
            return PAINTED;
        }
    }

    /** Whether the 128x128 textures are in use (face parts, form hair): painted and HD both. */
    public static boolean hiRes() {
        return get() != CLASSIC;
    }

    /** The folder race skins are read from. */
    public static String raceFolder() {
        return switch (get()) {
            case HD -> "race_hd";
            case CLASSIC -> "race";
            default -> "race_painted";
        };
    }

    /** The folder generated bodies and their outfit are read from. */
    public static String bodyFolder() {
        return switch (get()) {
            case HD -> "body_hd";
            case CLASSIC -> "body";
            default -> "body_painted";
        };
    }
}
