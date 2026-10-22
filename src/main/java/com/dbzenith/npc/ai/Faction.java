package com.dbzenith.npc.ai;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.npc.KiFighter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Who stands with whom (there were no factions before the combat AI). Vanilla scoreboard teams come first: two
 * entities that are both on teams are allies on the same team and enemies on different ones (so commands, maps and
 * tests can set up any sides). Otherwise NPC fighters go by faction:
 * <ul>
 *   <li>the Frieza Force (soldiers, Saibamen, Frieza) against the Namekians;</li>
 *   <li>the damned of Limbo against the other world's ogres;</li>
 *   <li>the Red Ribbon Army keeps to itself; Broly and the tournament fighters have no allies.</li>
 *   <li>townsfolk stand together against the Frieza Force, the Red Ribbon Army and the damned.</li>
 * </ul>
 */
public enum Faction {
    FRIEZA_FORCE, RED_RIBBON, NAMEKIANS, OTHERWORLD, DAMNED, LONER, TOURNAMENT,
    /** The people of a town (docs/LIVING_WORLD.md): they stand together. */
    TOWNSFOLK;

    public boolean alliedWith(Faction o) {
        return this == o && this != LONER && this != TOURNAMENT;
    }

    public boolean enemyOf(Faction o) {
        return pair(this, o, FRIEZA_FORCE, NAMEKIANS) || pair(this, o, DAMNED, OTHERWORLD)
                || pair(this, o, TOWNSFOLK, FRIEZA_FORCE) || pair(this, o, TOWNSFOLK, RED_RIBBON) || pair(this, o, TOWNSFOLK, DAMNED);   // invaders
    }

    private static boolean pair(Faction a, Faction b, Faction x, Faction y) {
        return a == x && b == y || a == y && b == x;
    }

    public static Faction of(LivingEntity e) {
        return e instanceof KiFighter k ? k.aiProfile().faction() : null;
    }

    /** Allies: never targeted, never hit on purpose, protected when hurt. */
    public static boolean allied(LivingEntity a, LivingEntity b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getTeam() != null && b.getTeam() != null) return a.isAlliedTo(b);
        if (a.getTeam() != null || b.getTeam() != null) return false;
        Faction fa = of(a), fb = of(b);
        return fa != null && fb != null && fa.alliedWith(fb);
    }

    /** Enemies by side (players are judged by the fighters' own target goals, not here). */
    public static boolean enemies(LivingEntity a, LivingEntity b) {
        if (a == null || b == null || a == b) return false;
        if (a.getTeam() != null && b.getTeam() != null) return !a.isAlliedTo(b);
        if (a instanceof Player || b instanceof Player) return false;
        Faction fa = of(a), fb = of(b);
        return fa != null && fb != null && fa.enemyOf(fb);
    }

    /** Whether enemies of other factions are fought on sight (config), not only when already fighting. */
    static boolean wars() {
        try {
            return DBZConfig.SERVER.aiFactionWars.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
