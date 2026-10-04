package com.dbzenith.ki;

import com.dbzenith.data.PlayerData;

/** Aura color source. Forms (Phase 2) and races (Phase 3) will override the default. */
public final class Aura {
    public static final int DEFAULT_COLOR = 0xD9F2FF;

    private Aura() {}

    public static int color(PlayerData data) {
        return DEFAULT_COLOR;
    }
}
