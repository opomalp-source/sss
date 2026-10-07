package com.dbzenith.combat.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One melee move (CX-19), from {@code data/<ns>/combat/moves/<id>.json}. Times are in ticks (20 a second): the move
 * winds up for {@code startup}, can hit for {@code active}, then recovers for {@code recovery}. See docs/COMBAT.md for
 * every field.
 */
public final class Move {
    public enum Button { LIGHT, HEAVY }

    /** Which way the fighter is pushing (or looking, for up and down) when the button is pressed. */
    public enum Dir { ANY, NEUTRAL, FORWARD, BACK, SIDE, UP, DOWN }

    public enum Where { ANY, GROUND, AIR }

    /** What a hit does to the body it lands on. */
    public enum Launch { NONE, AWAY, UP, DOWN, SPIKE, KNOCKDOWN }

    public enum Shape { SPHERE, CONE, BOX }

    public final String id;
    public final Button button;
    public final Dir dir;
    public final Where where;
    /** Moves this one may follow ("start": from nothing, or after a chain has run out). */
    public final List<String> after;
    public final int priority;

    public final int startup, active, recovery;
    /** From this tick (counted from the start), a move that has landed may be cancelled into its follow-ups. */
    public final int cancel;
    public final double damage, guardDamage;
    public final double kiCost, staminaCost;
    public final int hitstun;
    public final double knockback, lift;
    public final Launch launch;
    public final double launchPower;
    public final boolean unblockable;
    public final int armor;                        // ticks from the start during which hits do not interrupt it

    public final Shape shape;
    public final double range, radius, angle, height;
    public final int maxTargets;
    public final double lunge;                     // blocks moved toward the foe during startup

    public final String anim;
    public final String impact;
    public final String sound;

    private Move(String id, JsonObject j) {
        this.id = id;
        button = Button.valueOf(GsonHelper.getAsString(j, "button", "light").toUpperCase(Locale.ROOT));
        dir = Dir.valueOf(GsonHelper.getAsString(j, "direction", "any").toUpperCase(Locale.ROOT));
        where = Where.valueOf(GsonHelper.getAsString(j, "where", "any").toUpperCase(Locale.ROOT));
        List<String> a = new ArrayList<>();
        if (j.has("after")) for (JsonElement e : j.getAsJsonArray("after")) a.add(e.getAsString());
        else a.add("start");
        after = List.copyOf(a);
        priority = GsonHelper.getAsInt(j, "priority", 0);
        startup = Math.max(0, GsonHelper.getAsInt(j, "startup", 3));
        active = Math.max(1, GsonHelper.getAsInt(j, "active", 2));
        recovery = Math.max(0, GsonHelper.getAsInt(j, "recovery", 6));
        cancel = GsonHelper.getAsInt(j, "cancel", startup + active);
        damage = GsonHelper.getAsDouble(j, "damage", 1.0);
        guardDamage = GsonHelper.getAsDouble(j, "guard_damage", 1.0);
        kiCost = GsonHelper.getAsDouble(j, "ki_cost", 0);
        staminaCost = GsonHelper.getAsDouble(j, "stamina_cost", 2);
        hitstun = GsonHelper.getAsInt(j, "hitstun", 12);
        knockback = GsonHelper.getAsDouble(j, "knockback", 0.2);
        lift = GsonHelper.getAsDouble(j, "lift", 0.0);
        launch = Launch.valueOf(GsonHelper.getAsString(j, "launch", "none").toUpperCase(Locale.ROOT));
        launchPower = GsonHelper.getAsDouble(j, "launch_power", 1.0);
        unblockable = GsonHelper.getAsBoolean(j, "unblockable", false);
        armor = GsonHelper.getAsInt(j, "armor", 0);
        JsonObject hb = GsonHelper.getAsJsonObject(j, "hitbox", new JsonObject());
        shape = Shape.valueOf(GsonHelper.getAsString(hb, "shape", "sphere").toUpperCase(Locale.ROOT));
        range = GsonHelper.getAsDouble(hb, "range", 2.6);
        radius = GsonHelper.getAsDouble(hb, "radius", 1.0);
        angle = GsonHelper.getAsDouble(hb, "angle", 60);
        height = GsonHelper.getAsDouble(hb, "height", 2.0);
        maxTargets = GsonHelper.getAsInt(hb, "max_targets", 1);
        lunge = GsonHelper.getAsDouble(j, "lunge", 0.0);
        anim = GsonHelper.getAsString(j, "anim", "JAB_RIGHT");
        impact = GsonHelper.getAsString(j, "impact", "punch");
        sound = GsonHelper.getAsString(j, "sound", "whoosh");
    }

    public static Move parse(String id, JsonObject j) {
        return new Move(id, j);
    }

    public int total() {
        return startup + active + recovery;
    }

    /** Whether a tick (from the start) is in the active window. */
    public boolean isActive(int tick) {
        return tick >= startup && tick < startup + active;
    }

    public boolean follows(String previous) {
        return after.contains(previous);
    }

    static List<String> list(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }
}
