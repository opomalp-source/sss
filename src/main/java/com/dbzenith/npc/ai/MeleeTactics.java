package com.dbzenith.npc.ai;

import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Fighter;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.npc.KiFighter;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.LivingEntity;

/**
 * Blows, through the combat engine exactly as a player throws them (its moves, chains, cancel windows, cooldowns and
 * animations; stamina paid for each move). The string runs as long as the fighter's style allows, then a finisher
 * chosen for the moment:
 * <ul>
 *   <li>a guarding foe on the ground gets the sweep (it ignores guard), more often the more the foe turtles;</li>
 *   <li>a foe in the air below it gets the spike; a tactical fighter launches with the uppercut; an aggressive one
 *       rushes; otherwise the smash;</li>
 *   <li>a foe that dodges a lot is baited: after a whiff the fighter waits a moment before the next blow.</li>
 * </ul>
 */
final class MeleeTactics {
    static final double REACH = 3.0;

    private final KiFighter mob;
    private final NpcBrain brain;
    private long lastPress, pauseUntil;

    MeleeTactics(KiFighter mob, NpcBrain brain) {
        this.mob = mob;
        this.brain = brain;
    }

    /** Lights landed in the running chain. */
    static int chainLength(Fighter f, long now) {
        String last = f.move() != null ? f.move().id : now <= f.chainUntil() ? f.lastMove() : "start";
        if (last.startsWith("light_")) {
            try {
                return Integer.parseInt(last.substring(6));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * The next blow at {@code foe} if it is in reach. {@code finish}: go for the finisher now (an opening). Returns false
     * when it cannot (out of reach, or winded).
     */
    boolean strike(LivingEntity foe, long now, boolean finish) {
        if (mob.distanceTo(foe) > REACH + foe.getBbWidth() * 0.5) return false;
        mob.lookAt(EntityAnchorArgument.Anchor.EYES, foe.getEyePosition());
        mob.getLookControl().setLookAt(foe, 60f, 60f);
        Fighter f = CombatEngine.of(mob);
        if (f.move() != null && !f.landed()) {
            if (f.whiffing() && brain.foe.dodgy() > 0.3) pauseUntil = now + 6;      // they slipped it: bait the next one
            return true;
        }
        if (now < pauseUntil || now - lastPress < 3) return true;
        Moves.Input in = choose(foe, f, now, finish);
        Move m = Moves.select(in, f.move() != null ? f.move().id : now <= f.chainUntil() ? f.lastMove() : "start");
        if (m != null && brain.res.stamina < m.staminaCost) {
            if (in.button() == Move.Button.HEAVY) {                                  // no wind for a heavy: a light will do
                in = light();
                m = Moves.select(in, "start");
            }
            if (m != null && brain.res.stamina < m.staminaCost) return false;
        }
        brain.lowerGuard();
        CombatEngine.press(mob, in);
        lastPress = now;
        return true;
    }

    private Moves.Input light() {
        return new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, mob.onGround(), brain.forms.current().id());
    }

    Moves.Input choose(LivingEntity foe, Fighter f, long now, boolean finish) {
        Fighter g = CombatEngine.peek(foe);
        boolean guarding = g != null && g.state(now) == Fighter.State.GUARDING;
        boolean ground = mob.onGround();
        String form = brain.forms.current().id();
        double turtle = brain.foe.turtle();
        if (guarding && ground && brain.roll(11, 0.35 + 0.6 * turtle)) {             // break the turtle: the sweep
            brain.note("sweep vs guard");
            return new Moves.Input(Move.Button.HEAVY, Move.Dir.BACK, false, false, true, form);
        }
        int n = chainLength(f, now);
        if (finish || n >= brain.style.comboLength) {
            boolean foeAir = !foe.onGround();
            if (foeAir && !ground && mob.getY() > foe.getY() + 0.5) return new Moves.Input(Move.Button.HEAVY, Move.Dir.DOWN, false, true, false, form);
            if (brain.style == FightStyle.TACTICAL && ground && brain.roll(12, 0.5)) return new Moves.Input(Move.Button.HEAVY, Move.Dir.UP, true, false, true, form);
            if (brain.style == FightStyle.AGGRESSIVE && brain.roll(13, 0.5)) return new Moves.Input(Move.Button.HEAVY, Move.Dir.FORWARD, false, false, ground, form);
            return new Moves.Input(Move.Button.HEAVY, Move.Dir.NEUTRAL, false, false, ground, form);
        }
        return light();
    }
}
