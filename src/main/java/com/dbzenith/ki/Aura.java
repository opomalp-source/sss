package com.dbzenith.ki;

import com.dbzenith.data.PlayerData;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.Overdrive;

/** Aura color: Overdrive red, else the current form's aura (base = pale white-blue). Races recolor base in Phase 3. */
public final class Aura {
    public static final int DEFAULT_COLOR = 0xD9F2FF;

    private Aura() {}

    public static int color(PlayerData data) {
        if (data.getOverdriveLevel() > 0) return Overdrive.RED;
        return Forms.byId(data.getFormId()).auraColor();
    }
}
