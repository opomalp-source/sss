package com.dbzenith.npc.ai;

import java.util.Locale;

/**
 * What an NPC fighter can choose to do. Each holds for at least {@link #commit} ticks once chosen (no twitching from
 * one to the next), and belongs to a {@link Kind} for learning what pays off.
 */
public enum Action {
    IDLE(0, Kind.MOVE),
    APPROACH(8, Kind.MOVE),
    PRESSURE(12, Kind.MELEE),
    PUNISH(8, Kind.MELEE),
    INTERRUPT(10, Kind.MELEE),
    KEEP_DISTANCE(14, Kind.MOVE),
    STRAFE(16, Kind.MOVE),
    GUARD(10, Kind.DEFENCE),
    EVADE(6, Kind.DEFENCE),
    KI_VOLLEY(8, Kind.KI),
    BIG_TECHNIQUE(12, Kind.KI),
    CHARGE_KI(30, Kind.POWER),
    TRANSFORM(10, Kind.POWER),
    REVERT(6, Kind.POWER),
    RETREAT(30, Kind.MOVE),
    PURSUE(12, Kind.MOVE),
    PROTECT_ALLY(20, Kind.MELEE);

    public enum Kind { MOVE, MELEE, DEFENCE, KI, POWER }

    /** Ticks it holds once chosen, at least. */
    public final int commit;
    public final Kind kind;

    Action(int commit, Kind kind) {
        this.commit = commit;
        this.kind = kind;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
