package com.dbzenith.living;

import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;

import java.util.List;
import java.util.Locale;

/**
 * What a resident does for a living. It picks the resident's schedule ({@code data/<ns>/schedules/<id>.json}), its
 * workplace, the techniques it fights with, whether it fights at all (civilians flee and hide; fighters defend the
 * town), what it knows (merchants: other towns; guards: threats; masters: techniques and strong fighters) and what it
 * can offer in a conversation.
 */
public enum Occupation {
    //          fighter  workplace       techniques
    CITIZEN(false, Poi.Kind.PLAZA, List.of()),
    MERCHANT(false, Poi.Kind.SHOP, List.of()),
    COOK(false, Poi.Kind.TAVERN, List.of()),
    STUDENT(true, Poi.Kind.DOJO, List.of(Techniques.KI_BLAST)),
    MASTER(true, Poi.Kind.DOJO, List.of(Techniques.KI_BLAST, Techniques.WAVE_BEAM)),
    GUARD(true, Poi.Kind.PATROL, List.of(Techniques.KI_BLAST)),
    TRAVELER(true, Poi.Kind.GATE, List.of(Techniques.KI_BLAST)),
    /** A researcher (Capsule Corporation's labs and the like): no fighter. */
    SCIENTIST(false, Poi.Kind.LAB, List.of()),
    /** Works the fields round a village: no fighter. */
    FARMER(false, Poi.Kind.FIELD, List.of()),
    /** A village's elder (Namek): keeps the village from the elder's seat, knows all its news; no fighter. */
    ELDER(false, Poi.Kind.PLAZA, List.of());

    /** Fights (and defends the town) rather than flees. */
    public final boolean fighter;
    /** Where it works. */
    public final Poi.Kind workplace;
    /** The techniques it knows from its trade (a master teaches from these and any it learned). */
    public final List<Technique> techniques;

    Occupation(boolean fighter, Poi.Kind workplace, List<Technique> techniques) {
        this.fighter = fighter;
        this.workplace = workplace;
        this.techniques = techniques;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "living.dbzenith.occupation." + id();
    }

    public static Occupation byId(String id) {
        for (Occupation o : values()) if (o.id().equalsIgnoreCase(id)) return o;
        return null;
    }

    public static Occupation of(NamedFighter c) {
        Occupation o = c == null ? null : byId(c.occupation);
        return o == null ? CITIZEN : o;
    }
}
