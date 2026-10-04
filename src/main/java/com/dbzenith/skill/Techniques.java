package com.dbzenith.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in techniques. Original designs inspired by classic archetypes (basic blast, wave beam,
 * rapid volley, cutter disk, homing orb). Phase 3 turns this into a learnable library with a deck.
 */
public final class Techniques {
    private static final Map<String, Technique> BY_ID = new LinkedHashMap<>();

    //                                                    id              cost  mult  speed size  cd  n  spread pierce homing boom  color     life
    public static final Technique KI_BLAST = add(new Technique("ki_blast", 20, 1.0, 1.6f, 0.5f, 10, 1, 0f, 0, false, 0f, 0x7FD4FF, 60, Technique.Style.BALL));
    public static final Technique WAVE_BEAM = add(new Technique("wave_beam", 80, 3.0, 2.4f, 1.2f, 60, 1, 0f, 0, false, 1.5f, 0x4FA8FF, 80, Technique.Style.BALL));
    public static final Technique RAPID_VOLLEY = add(new Technique("rapid_volley", 40, 0.4, 1.8f, 0.35f, 30, 6, 7f, 0, false, 0f, 0xFFE070, 50, Technique.Style.BALL));
    public static final Technique CUTTER_DISK = add(new Technique("cutter_disk", 50, 1.5, 1.4f, 0.9f, 40, 1, 0f, 3, false, 0f, 0xFFF4A0, 80, Technique.Style.DISK));
    public static final Technique HOMING_ORB = add(new Technique("homing_orb", 35, 0.8, 0.9f, 0.6f, 25, 1, 0f, 0, true, 0f, 0xC77DFF, 100, Technique.Style.BALL));

    private Techniques() {}

    private static Technique add(Technique t) {
        BY_ID.put(t.id(), t);
        return t;
    }

    public static Technique byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Technique> all() {
        return List.copyOf(BY_ID.values());
    }
}
