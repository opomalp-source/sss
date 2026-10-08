package com.dbzenith.style;

/**
 * The animation slots a style can fill (CX-20), each chosen on its own in the K menu's Styles page. {@code state} is
 * the motion engine's state the slot plays for, and {@code fighting} whether it is the fighting set's (PvP on).
 */
public enum StyleSlot {
    IDLE("idle", "idle", false),
    WALK("walk", "walk", false),
    SPRINT("sprint", "sprint", false),
    HOVER("hover", "hover", false),
    FLIGHT("flight", "cruise", false),
    FAST_FLIGHT("fast_flight", "fast", false),
    CHARGE("charge", "charge", false),
    FIGHT_STANCE("fight_stance", "idle", true),
    FIGHT_STEPS("fight_steps", "walk", true);

    public static final StyleSlot[] ALL = values();

    public final String id, state;
    public final boolean fighting;

    StyleSlot(String id, String state, boolean fighting) {
        this.id = id;
        this.state = state;
        this.fighting = fighting;
    }

    public String nameKey() {
        return "style.dbzenith.slot." + id;
    }

    public static StyleSlot byId(String id) {
        for (StyleSlot s : ALL) if (s.id.equals(id)) return s;
        return null;
    }
}
