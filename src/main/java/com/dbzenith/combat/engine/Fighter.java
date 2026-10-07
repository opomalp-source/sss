package com.dbzenith.combat.engine;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One fighter's combat state (CX-19), for players and NPCs alike, kept by {@link CombatEngine} on the server. Holds the
 * move being thrown (and the chain it belongs to), a buffered press, and, as a victim, the combo being taken: how many
 * hits, juggles and floored hits, and the flight a blow sent it on (for wall and ground slams).
 */
public final class Fighter {
    public enum State { IDLE, ATTACKING, GUARDING, STUNNED, LAUNCHED, KNOCKDOWN, DODGING, CHARGING, DEAD }

    final LivingEntity entity;

    // ---------------------------------------------------------------- attacking
    Move move;
    int moveTick;
    boolean landed;
    final IntOpenHashSet struck = new IntOpenHashSet();
    String lastMove = "start";
    long chainUntil;
    Moves.Input buffered;
    long bufferedAt;
    long lastPressTick;

    // ---------------------------------------------------------------- taking a combo
    int comboHits, juggleHits, downedHits;
    int comboFrom = -1;
    long lastHitAt = Long.MIN_VALUE / 2;
    long stunUntil;
    boolean juggled, spiked;
    long flightUntil;                    // knocked away: a wall within this time is a wall slam
    LivingEntity flightBy;
    double lastBlow;                     // the last blow's damage, for slam bonuses
    long wakeUntil;                      // just got up: untouchable until

    // ---------------------------------------------------------------- evasion (phase 3)
    long vanishUntil, vanishReadyAt;     // a dash opens a vanish window; vanishes have a cooldown
    long counterUntil;                   // after a vanish or a perfect guard: a press throws the counter
    int counterTarget = -1;
    LivingEntity superDashTarget;        // rushing at a foe
    long superDashUntil;
    boolean wasDowned;

    Fighter(LivingEntity entity) {
        this.entity = entity;
    }

    /** Tests and dev: treat this fighter as juggled (in the air from a launch). */
    public void markJuggled() {
        juggled = true;
    }

    public Move move() {
        return move;
    }

    public int moveTick() {
        return moveTick;
    }

    public int comboHits() {
        return comboHits;
    }

    public String lastMove() {
        return lastMove;
    }

    /** What the fighter is doing, as the state machine sees it. */
    public State state(long now) {
        if (!entity.isAlive()) return State.DEAD;
        if (com.dbzenith.combat.CombatMoves.isDowned(entity, now)) return State.KNOCKDOWN;
        if (juggled && !entity.onGround()) return State.LAUNCHED;
        if (now < stunUntil || com.dbzenith.registry.ModEffects.isStunned(entity)) return State.STUNNED;
        if (move != null) return State.ATTACKING;
        if (entity instanceof net.minecraft.world.entity.player.Player p) {
            var d = com.dbzenith.data.ModCapabilities.get(p).orElse(null);
            if (d != null && d.isGuarding()) return State.GUARDING;
            if (d != null && d.isCharging()) return State.CHARGING;
        }
        return State.IDLE;
    }

    Vec3 position() {
        return entity.position();
    }
}
