package com.dbzenith.npc.ai;

import com.dbzenith.combat.KnockOut;
import com.dbzenith.npc.KiFighter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Who to fight. The candidates: the current target (from the fighter's target goals: players for hostile fighters,
 * whoever hurt it), whoever hurt it last, whoever is beating on an ally, and enemies of its faction that are fighting
 * nearby (or any in reach, with {@code npc_ai.factionWars}). Each gets a threat score:
 * <ul>
 *   <li>more for the one it is already fighting (no flip-flopping: a newcomer must beat it by a third);</li>
 *   <li>more for whoever targets it and whoever hurt it lately;</li>
 *   <li>more the stronger they stand against it, a little less the farther;</li>
 *   <li>more for whoever is hurting a weakened ally (by honor), and for a foe nearly beaten (by aggression).</li>
 * </ul>
 * Never allies, never the knocked out (finishing them is the grip system's business), never spectators or creative
 * players, never anyone outside the fighter's bounds.
 */
final class ThreatAssessment {
    static final double RANGE = 24, SWITCH_MARGIN = 1.33;

    private ThreatAssessment() {}

    static LivingEntity choose(KiFighter mob, NpcBrain b, long now, boolean scan) {
        LivingEntity current = mob.getTarget();
        if (mob instanceof com.dbzenith.tournament.TournamentFighter) return valid(mob, current) ? current : null;   // the bracket decides
        List<LivingEntity> cand = new ArrayList<>(6);
        if (valid(mob, current)) cand.add(current);
        LivingEntity hurtBy = mob.getLastHurtByMob();
        if (hurtBy != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < 200 && valid(mob, hurtBy) && !cand.contains(hurtBy)) cand.add(hurtBy);
        if (b.allyAttacker != null && valid(mob, b.allyAttacker) && !cand.contains(b.allyAttacker)) cand.add(b.allyAttacker);
        if (scan) {
            boolean wars = Faction.wars();
            for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(RANGE),
                    e -> e != mob && e instanceof KiFighter && Faction.enemies(mob, e))) {
                boolean fighting = e instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null;
                if ((wars || fighting) && valid(mob, e) && !cand.contains(e)) cand.add(e);
            }
        }
        if (cand.isEmpty()) return null;
        LivingEntity best = null;
        double bestScore = -1, currentScore = -1;
        for (LivingEntity c : cand) {
            double s = score(mob, b, c, now);
            if (c == current) currentScore = s;
            if (s > bestScore) {
                bestScore = s;
                best = c;
            }
        }
        if (current != null && currentScore >= 0 && best != current && bestScore < currentScore * SWITCH_MARGIN) return current;
        return best;
    }

    static double score(KiFighter mob, NpcBrain b, LivingEntity c, long now) {
        Personality p = b.personality;
        double s = 1.0;
        if (c == mob.getTarget()) s += 0.35;
        if (c instanceof net.minecraft.world.entity.Mob m && m.getTarget() == mob) s += 0.4;
        if (AiEvents.lastAttacker(mob, now, 100) == c.getId()) s += 0.5;
        double rel = Power.standing(c) / Math.max(1, Power.standing(mob));
        s += Math.max(0.2, Math.min(3, rel)) * 0.2;
        s -= mob.distanceTo(c) * 0.03;
        if (c == b.allyAttacker) s += 0.6 * p.honor;
        if (Power.health(c) < 0.3) s += 0.2 * p.aggression;
        return Math.max(0, s);
    }

    /** Why {@code e} is not a valid foe (for the debug readout), or null if it is. */
    static String why(KiFighter mob, LivingEntity e) {
        if (e == null) return "gone";
        if (!e.isAlive() || e.isRemoved()) return "dead";
        if (e.isSpectator() || e instanceof Player p && (p.isCreative() || p.isSpectator())) return "spectator or creative";
        if (e.level() != mob.level()) return "another world";
        if (Faction.allied(mob, e)) return "an ally";
        if (KnockOut.isDown(e)) return "knocked out";
        if (mob.distanceToSqr(e) > 48 * 48) return "too far";
        if (!mob.canAttack(e)) return "can't be attacked";
        return null;
    }

    static boolean valid(KiFighter mob, LivingEntity e) {
        if (e == null || e == mob || !e.isAlive() || e.isRemoved() || e.isSpectator() || e.level() != mob.level()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (Faction.allied(mob, e) || KnockOut.isDown(e)) return false;
        if (mob.distanceToSqr(e) > 48 * 48) return false;
        return mob.canAttack(e);
    }

    /** Whether {@code e} is engaged with {@code mob} (it targets it). */
    static boolean engaged(Entity e, KiFighter mob) {
        return e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == mob;
    }
}
