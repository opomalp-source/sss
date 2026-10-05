package com.dbzenith.ki;

import com.dbzenith.data.PlayerData;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.Kaioken;
import com.dbzenith.transform.Overdrive;

/** Aura color: Overdrive red, else the current form's aura (base = pale white-blue). Races recolor base in Phase 3. */
public final class Aura {
    public static final int DEFAULT_COLOR = 0xD9F2FF;

    private Aura() {}

    public static int color(PlayerData data) {
        if (data.getOverdriveLevel() > 0) return Overdrive.RED;
        if (data.getKaiokenStage() > 0) {                  // Kaioken: red, or red shot through a god form's aura
            if (!data.isTransformed()) return Kaioken.RED;
            int f = Forms.byId(data.getFormId()).auraColor();
            return ((f >> 16 & 255) + 0xFF) / 2 << 16 | ((f >> 8 & 255) + 0x2A) / 2 << 8 | ((f & 255) + 0x1E) / 2;
        }
        if (data.isTransforming()) return Forms.byId(data.getTransformTarget()).auraColor();   // the new power showing through
        if (!data.isTransformed()) {
            int variant = data.getVariant().auraColor();
            return variant >= 0 ? variant : com.dbzenith.race.Races.of(data.getRace()).auraColor();
        }
        return Forms.byId(data.getFormId()).auraColor();
    }
}
