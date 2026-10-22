package com.dbzenith.npc.ai;

import com.dbzenith.combat.KnockOut;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Evasion;
import com.dbzenith.combat.engine.Fighter;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.BossFighter;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.skill.KiBeamEntity;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.Technique;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One NPC fighter's mind (docs/COMBAT_AI.md). A small hierarchical state machine on top (no foe: idle; a power-up
 * under way: committed to it; otherwise fighting), and in the fight a utility choice among {@link Action}s, made every
 * few ticks (staggered across fighters) from a {@link Situation} read off the world, with commitment, hysteresis,
 * reaction delays and a little deterministic imperfection. Executors carry the chosen action out every tick; a
 * reactive layer handles what needs exact timing (guards, parries, vanishes, counters, sidesteps), never faster than
 * the fighter's reaction time.
 */
public final class NpcBrain {
    public enum Outcome { WON, LOST, FLED }

    final KiFighter mob;
    final AiProfile profile;
    Personality personality;
    FightStyle style;
    AiProfile.Tier tier;
    final NpcResources res = new NpcResources();
    final NpcForms forms = new NpcForms();
    final Locomotion move;
    final MeleeTactics melee;
    final KiTactics ki;
    final FightLog log = new FightLog();
    OpponentProfile foe = new OpponentProfile();
    UUID foeId;
    String foeName = "";
    final long seed;

    Action action = Action.IDLE;
    long actionSince, commitUntil;
    String reason = "";
    String formNote = "";
    private java.util.List<Temper.Shift> shifts = java.util.List.of();
    private Temper temperCache;
    long counter, nextDecision = Long.MIN_VALUE, lastDecision;
    private final Deque<String> notes = new ArrayDeque<>();
    int fights;
    long fightStart = -1, lastFoughtTick = Long.MIN_VALUE / 2, lastActiveTick = Long.MIN_VALUE / 2;
    boolean lowHealthReacted, powering;
    long foeTransformedAt = Long.MIN_VALUE / 2, foeNewTechAt = Long.MIN_VALUE / 2, allyFellAt = Long.MIN_VALUE / 2, lowHealthAt = Long.MIN_VALUE / 2;
    private final Set<String> seenTechniques = new HashSet<>();
    private String foeForm = "";

    // the reactive layer
    private long reactAt = -1;
    private int react;                    // 0 none, 1 parry, 2 vanish, 3 counter, 4 sidestep, 5 block
    private int reactedToMove = -1;       // the foe's move tick + id hash already answered
    private long vanishReadyAt, sidestepReadyAt, guardRaisedAt, dashReadyAt;
    int incomingEta = -1;
    private Vec3 incomingDir = Vec3.ZERO;

    // allies
    final List<KiFighter> squad = new ArrayList<>();
    private long squadAt = Long.MIN_VALUE / 2;
    LivingEntity allyAttacker;
    private LivingEntity allyInDanger;
    boolean squadRetreat, rangedRole;
    double flankAngle;

    /** Tests: only targets inside this box are fought (other tests run alongside). */
    public AABB arena;
    Situation lastSituation;
    UtilityScorer.Result lastScores;
    private boolean wasFlyingOnLoad;
    private LivingEntity watchedTarget;

    public NpcBrain(KiFighter mob) {
        this.mob = mob;
        this.profile = mob.aiProfile();
        this.seed = mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits();
        this.personality = profile.born(seed);
        this.style = personality.style();
        this.tier = effectiveTier(profile.tier());
        this.move = new Locomotion(mob);
        this.melee = new MeleeTactics(mob, this);
        this.ki = new KiTactics(mob, this);
        res.setLevel(mob.fighterLevel());
    }

    static AiProfile.Tier effectiveTier(AiProfile.Tier t) {
        int cap;
        try {
            cap = DBZConfig.SERVER.aiQuality.get();
        } catch (IllegalStateException e) {
            cap = 2;
        }
        return AiProfile.Tier.values()[Math.min(t.ordinal(), cap)];
    }

    // ------------------------------------------------------------------ small helpers

    public Personality personality() {
        return personality;
    }

    public void setPersonality(Personality p) {
        personality = p;
        style = p.style();
        temperCache = null;
    }

    /** The traits it fights with: its personality, shifted by any Nemesis traits it has. */
    public Temper temper() {
        if (temperCache == null) temperCache = Temper.of(personality, shifts);
        return temperCache;
    }

    /** The Nemesis system gives a named character its traits' shifts (none for ordinary fighters). */
    public void setShifts(java.util.List<Temper.Shift> s) {
        shifts = s == null ? java.util.List.of() : java.util.List.copyOf(s);
        temperCache = null;
    }

    public NpcResources resources() {
        return res;
    }

    public NpcForms forms() {
        return forms;
    }

    public Action action() {
        return action;
    }

    public OpponentProfile opponent() {
        return foe;
    }

    public FightLog fightLog() {
        return log;
    }

    public Locomotion locomotion() {
        return move;
    }

    /** How well it thinks, 0..1. */
    double quality() {
        double q = switch (tier) {
            case LIGHT -> 0.5;
            case NORMAL -> 0.75;
            case ELITE -> 1.0;
        };
        return Math.min(1.0, q + 0.1 * fights / (fights + 5.0));
    }

    /** Ticks it takes to react to what it sees (never under 3). */
    int reaction() {
        int base;
        try {
            base = DBZConfig.SERVER.aiReactionTicks.get();
        } catch (IllegalStateException e) {
            base = 8;
        }
        double f = switch (tier) {
            case LIGHT -> 1.4;
            case NORMAL -> 1.0;
            case ELITE -> 0.55;
        };
        return Math.max(3, (int) Math.round(base * f - Math.min(2, mob.fighterLevel() / 40.0)));
    }

    int decisionInterval() {
        int base;
        try {
            base = DBZConfig.SERVER.aiDecisionTicks.get();
        } catch (IllegalStateException e) {
            base = 6;
        }
        double f = switch (tier) {
            case LIGHT -> 2.0;
            case NORMAL -> 1.0;
            case ELITE -> 0.5;
        };
        return Math.max(2, (int) Math.round(base * f));
    }

    static double mistakes() {
        try {
            return DBZConfig.SERVER.aiMistakes.get();
        } catch (IllegalStateException e) {
            return 1;
        }
    }

    double unit(int salt) {
        return AiRandom.unit(seed, counter, salt);
    }

    boolean roll(int salt, double p) {
        return AiRandom.chance(seed, counter, salt, p);
    }

    public void note(String what) {
        String line = mob.level().getGameTime() + ": " + what;
        if (!notes.isEmpty() && notes.peekLast().endsWith(what)) return;
        notes.addLast(line);
        while (notes.size() > 6) notes.removeFirst();
    }

    public List<String> notes() {
        return new ArrayList<>(notes);
    }

    void lowerGuard() {
        if (res.guarding) {
            NpcGuard.lower(res);
            mob.setAiFlag(KiFighter.AI_GUARDING, false);
        }
    }

    void raiseGuard(long now) {
        if (NpcGuard.raise(res, now)) {
            guardRaisedAt = res.guardStartTick;
            mob.setAiFlag(KiFighter.AI_GUARDING, true);
        }
    }

    // ------------------------------------------------------------------ every tick, whether fighting or not

    /** Pools, form upkeep, flight upkeep, the fight ending. Called from the fighter's own tick on the server. */
    public void passiveTick(long now) {
        LivingEntity tgt = mob.getTarget();                                        // a target dropped by anyone: say why (debug)
        if (tgt != watchedTarget) {
            if (tgt == null && watchedTarget != null) {
                String w = ThreatAssessment.why(mob, watchedTarget);
                note("lost " + watchedTarget.getName().getString() + ": " + (w == null ? "dropped by its target goal (out of sight?)" : w));
            }
            watchedTarget = tgt;
        }
        if (wasFlyingOnLoad) {                                                      // saved in the air: come down safely
            wasFlyingOnLoad = false;
            mob.setNoGravity(false);
            move.landedAt = now;
        }
        if (now - move.landedAt < 200 && !mob.onGround()) mob.resetFallDistance();   // a flier never takes a fall from its own landing
        boolean active = now - lastActiveTick <= 1;
        boolean down = KnockOut.isDown(mob) || mob.isNoAi();
        if (down || !active) {
            if (powering) setPowering(false);
            if (ki.charging != null) ki.fizzle(false);
            lowerGuard();
            if (forms.transforming()) {
                forms.stop();
                mob.setAiFlag(KiFighter.AI_TRANSFORMING, false);
            }
            if (move.flying && (down || now - lastActiveTick > 20)) {
                if (!down && move.heightAboveGround() > 1.2) {                     // no fight left: come down gently
                    mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.5, 0, 0.5).add(0, -0.4, 0));
                    mob.resetFallDistance();
                } else {
                    move.land();
                    move.landedAt = now;
                }
            }
        }
        boolean fighting = now - lastFoughtTick < 200;
        res.tick(now, powering, fighting);
        Form f = forms.current();
        if (!f.isBase()) {
            res.drainKi(NpcForms.drainPerSecond(f, res) / 20.0, now);
            res.drainStamina(f.staminaDrainPercent() * 0.75 / 20.0, now);
            if (res.ki <= 0) {
                forms.enter(mob, Forms.byId(f.parent()), now);                    // exhausted: drops out of it, as players do
                AiEvents.onTransform(mob, forms.form);
                formNote = "dropped out of " + f.id() + ": out of ki";
                note(formNote);
            } else if (!fighting && !forms.transforming() && now >= forms.cooldownUntil) {
                forms.enter(mob, Forms.BASE, now);                                // the fight is over: powers down to save ki
                AiEvents.onTransform(mob, forms.form);
                formNote = "powered down after the fight";
            }
        }
        if (move.flying) {
            double drain;
            try {
                drain = DBZConfig.SERVER.flightKiPercentPerSecond.get();
            } catch (IllegalStateException e) {
                drain = 1.5;
            }
            res.drainKi(res.maxKi * drain / 100.0 / 20.0 * com.dbzenith.galaxy.planet.PlanetEnvironment.flightFactor(mob.level()), now);
            if (res.kiShare() < 0.03) {
                move.land();
                note("landed: out of ki");
            }
        }
        if (fightStart >= 0 && !fighting && now - lastFoughtTick > 300) endFight(action == Action.RETREAT ? Outcome.FLED : null, now);
    }

    // ------------------------------------------------------------------ the fight (from the goal, every tick)

    /** Whether it has (or should pick) a fight now: the goal's canUse. */
    public boolean wantsToFight(long now) {
        if (mob.isNoAi() || KnockOut.isDown(mob) || !mob.isAlive()) return false;
        if (ThreatAssessment.valid(mob, mob.getTarget())) return true;
        LivingEntity hurtBy = mob.getLastHurtByMob();                             // hurt lately: answer it (the threat assessment picks whom)
        if (hurtBy != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < 200 && ThreatAssessment.valid(mob, hurtBy)) return true;
        refreshSquad(now, false);                                                  // every two seconds at most: an ally in trouble?
        return allyAttacker != null && ThreatAssessment.valid(mob, allyAttacker);
    }

    public void tick(long now) {
        lastActiveTick = now;
        if (forms.transforming()) {                                               // committed: the power-up
            move.stop();
            move.hover(now);
            mob.setAiFlag(KiFighter.AI_TRANSFORMING, true);
            if (forms.tickPowerUp(mob, res, now)) {
                AiEvents.onTransform(mob, forms.form);
                note("transformed into " + forms.form);
            }
            if (!forms.transforming()) mob.setAiFlag(KiFighter.AI_TRANSFORMING, false);
            lastFoughtTick = now;
            return;
        }
        if (now % 2 == 0) scanIncoming(now);
        reactive(now);
        if (now >= nextDecision) decide(now);
        execute(now);
    }

    // ------------------------------------------------------------------ perception

    /** Reads the world into a situation, for the current target. */
    Situation perceive(LivingEntity t, long now) {
        Situation s = new Situation();
        s.personality = personality;
        s.temper = temper();
        s.style = style;
        s.quality = quality();
        s.experience = fights / (fights + 5.0);
        s.boss = mob instanceof BossFighter;
        s.canFly = Locomotion.mayFly(mob, this);
        s.flying = move.flying;
        s.meleeOnly = mob.techniques().isEmpty();
        s.noRetreat = mob.aiBounds() != null;
        s.health = Power.health(mob);
        s.ki = res.kiShare();
        s.stamina = res.staminaShare();
        s.guard = res.guardShare();
        s.quickReady = ki.quick(now) != null;
        s.bigReady = ki.big(now) != null;
        s.vanishReady = now >= vanishReadyAt && res.ki >= vanishCost();
        s.airborne = !mob.onGround();
        Form cur = forms.current();
        s.inForm = !cur.isBase();
        Form next = transformOn() ? forms.nextUp(profile, Power.equivalentLevel(mob.fighterLevel())) : null;
        s.canTransformUp = next != null && now >= forms.cooldownUntil && !forms.transforming();
        if (next != null) {
            s.nextFormFactor = NpcForms.factor(next) / NpcForms.factor(cur);
            s.kiAfterForm = (res.ki - NpcForms.cost(res) - NpcForms.drainPerSecond(next, res) * 10) / res.maxKi;
        }
        s.current = action;
        s.committed = now < commitUntil;
        for (Action a : Action.values()) s.bias[a.ordinal()] = log.bias(a) + eventBias(a, now);
        s.allies = squad.size();
        s.allyInDanger = allyInDanger != null && allyAttacker != null && allyAttacker != t;
        s.squadRetreat = squadRetreat || now - allyFellAt < 100 && temper().courage < 0.4;
        s.rangedRole = rangedRole;
        s.incoming = incomingEta;
        s.profile = foe;
        if (t == null) {
            s.hasTarget = false;
            return s;
        }
        s.distance = mob.distanceTo(t);
        s.lineOfSight = mob.hasLineOfSight(t);
        s.foeHealth = Power.health(t);
        s.ratio = Power.standing(mob) / Math.max(1, Power.standing(t));
        Fighter g = CombatEngine.peek(t);
        Fighter.State gs = g == null ? Fighter.State.IDLE : g.state(now);
        s.foeAttacking = g != null && g.windupLeft() >= 0 && s.distance < 5;
        s.foeRecovering = g != null && g.whiffing();
        s.foeGuarding = gs == Fighter.State.GUARDING;
        s.foeStunned = gs == Fighter.State.STUNNED || gs == Fighter.State.LAUNCHED || gs == Fighter.State.KNOCKDOWN;
        s.foeDowned = KnockOut.isDown(t);
        s.foeAirborne = !t.onGround() && move.groundBelow(t.position()) < t.getY() - 2.5;
        s.foeCharging = gs == Fighter.State.CHARGING;
        if (t instanceof Player p) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            if (d != null) {
                s.foeKi = d.getKi() / Math.max(1, d.getDerived().maxKi());
                s.foeExhausted = d.getStamina() < d.getDerived().maxStamina() * 0.15;
                s.foeCharging |= d.isCharging() || d.isTransforming() || p instanceof ServerPlayer sp && com.dbzenith.skill.KiCharge.isCharging(sp);
            }
        } else if (t instanceof KiFighter k && k.brainOrNull() != null) {
            NpcBrain o = k.brainOrNull();
            s.foeKi = o.res.kiShare();
            s.foeExhausted = o.res.staminaShare() < 0.15;
            s.foeCharging |= o.powering || o.forms.transforming() || o.ki.charging != null;
        }
        s.foeJustTransformed = now - foeTransformedAt < 100;
        s.foeNewTechnique = now - foeNewTechAt < 100;
        return s;
    }

    /** Short-lived nudges from events (an ally fell, its own health dropping low, a new technique seen). */
    private double eventBias(Action a, long now) {
        double b = 0;
        if (now - allyFellAt < 100 && temper().aggression > 0.6 && a == Action.PRESSURE) b += 0.2;          // avenging
        if (now - lowHealthAt < 200) {
            if (personality == Personality.COWARDLY && a == Action.RETREAT) b += 0.3;
            if (temper().recklessness > 0.6 && a == Action.BIG_TECHNIQUE) b += 0.2;                         // all in
        }
        if (now - foeNewTechAt < 100 && (a == Action.KEEP_DISTANCE || a == Action.GUARD)) b += 0.15 * temper().caution;
        String rep = foe.repeated();
        if (!rep.isEmpty() && a == Action.STRAFE) b += 0.1 * quality();                                         // read the habit
        return b;
    }

    boolean transformOn() {
        try {
            return DBZConfig.SERVER.aiTransform.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    double vanishCost() {
        double pct;
        try {
            pct = DBZConfig.SERVER.vanishKiPercent.get();
        } catch (IllegalStateException e) {
            pct = 4;
        }
        return res.maxKi * pct / 100.0;
    }

    /** Ki attacks coming at it: the soonest one's time to arrive, or -1. */
    void scanIncoming(long now) {
        incomingEta = -1;
        double best = Double.MAX_VALUE;
        AABB around = mob.getBoundingBox().inflate(24);
        double r = mob.getBbWidth() * 0.5 + 0.8;
        Vec3 me = mob.getBoundingBox().getCenter();
        for (KiBlastEntity b : mob.level().getEntitiesOfClass(KiBlastEntity.class, around, b -> b.getOwner() != mob)) {
            if (b.getOwner() instanceof LivingEntity o && Faction.allied(mob, o)) continue;
            Vec3 v = b.getDeltaMovement();
            double v2 = v.lengthSqr();
            if (v2 < 1e-3) continue;
            Vec3 rel = me.subtract(b.position());
            double tHit = rel.dot(v) / v2;
            if (tHit < 0 || tHit > 20) continue;
            double miss = rel.subtract(v.scale(tHit)).length();
            if (miss > r + b.getBbWidth() * 0.5) continue;
            if (tHit < best) {
                best = tHit;
                incomingDir = v.normalize();
            }
        }
        for (KiBeamEntity beam : mob.level().getEntitiesOfClass(KiBeamEntity.class, around, b -> b.getOwner() != mob)) {
            if (!(beam.getOwner() instanceof LivingEntity o) || Faction.allied(mob, o)) continue;
            Vec3 dir = beam.direction();
            Vec3 rel = me.subtract(beam.position());
            double along = rel.dot(dir);
            if (along < 0 || along > KiBeamEntity.MAX_RANGE) continue;
            if (rel.subtract(dir.scale(along)).length() > r + beam.getWidth()) continue;
            if (4 < best) {
                best = 4;
                incomingDir = dir;
            }
        }
        if (best < Double.MAX_VALUE) incomingEta = (int) Math.round(best);
    }

    // ------------------------------------------------------------------ deciding

    void decide(long now) {
        counter++;
        int dt = lastDecision == 0 ? decisionInterval() : (int) Math.max(1, now - lastDecision);
        lastDecision = now;
        int interval = decisionInterval();
        if (nextDecision == Long.MIN_VALUE) nextDecision = now + Math.floorMod(mob.getId(), interval);    // stagger fighters
        else nextDecision = now + interval;
        refreshSquad(now, false);
        LivingEntity t = ThreatAssessment.choose(mob, this, now, counter % 4 == 0);
        if (t != mob.getTarget()) {
            if (t != null) note("target " + t.getName().getString() + (t == allyAttacker ? " (protecting an ally)" : ""));
            mob.setTarget(t);
        }
        if (t != null) {
            if (!t.getUUID().equals(foeId)) newFoe(t, now);
            lastFoughtTick = now;
        }
        Situation s = perceive(t, now);
        if (t != null) {
            boolean firing = s.foeCharging || foe.ki > 0.3;
            foe.sample(dt, s.foeGuarding, s.foeCharging, !t.onGround(), s.distance, firing);
            observeForm(t, now);
            if (s.health < 0.3 && !lowHealthReacted) {                            // its own low health: an event
                lowHealthReacted = true;
                lowHealthAt = now;
                note("low health (" + personality.id() + ")");
            }
        }
        UtilityScorer.Result r = UtilityScorer.score(s);
        lastSituation = s;
        lastScores = r;
        Action best = pick(r);
        if (now < commitUntil && best != action && !urgent(best, s)) return;
        if (best != action) switchTo(best, r.why(best), now);
    }

    /** The choice: the best score, with a little deterministic noise and, now and then, the second best (a mistake). */
    Action pick(UtilityScorer.Result r) {
        double q = quality(), m = mistakes();
        Action best = null;
        double bs = UtilityScorer.NO;
        for (Action a : Action.values()) {
            double s = r.of(a);
            if (s == UtilityScorer.NO) continue;
            s += (unit(100 + a.ordinal()) - 0.5) * 0.12 * (1.25 - q) * m;
            if (s > bs) {
                bs = s;
                best = a;
            }
        }
        if (best == null) return Action.IDLE;
        double slip = (q >= 1 ? 0.02 : q >= 0.75 ? 0.05 : 0.10) * m;
        if (roll(99, slip)) {
            Action second = r.ranked(1);
            if (second != best && r.of(second) != UtilityScorer.NO && r.of(second) > r.of(best) - 0.35) {
                note("misjudged: " + second.id() + " over " + best.id());
                return second;
            }
        }
        return best;
    }

    /** What may cut a committed action short. */
    static boolean urgent(Action a, Situation s) {
        return a == Action.EVADE || a == Action.PUNISH || a == Action.INTERRUPT || a == Action.GUARD && s.foeAttacking
                || a == Action.RETREAT && s.health < 0.2 || a == Action.PROTECT_ALLY;
    }

    void switchTo(Action next, String why, long now) {
        log.switchTo(next);
        Action prev = action;
        if (prev == Action.GUARD) lowerGuard();
        if (prev == Action.CHARGE_KI) setPowering(false);
        if (prev == Action.BIG_TECHNIQUE && ki.charging != null) ki.fizzle(false);
        action = next;
        actionSince = now;
        commitUntil = now + next.commit;
        reason = why;
        switch (next) {
            case TRANSFORM -> {
                Form f = forms.nextUp(profile, Power.equivalentLevel(mob.fighterLevel()));
                if (f != null) {
                    forms.begin(mob, f, tier);
                    mob.setAiFlag(KiFighter.AI_TRANSFORMING, true);
                    formNote = "transforming into " + f.id() + ": " + why;
                    note(formNote);
                    lowerGuard();
                    if (ki.charging != null) ki.fizzle(false);
                }
            }
            case REVERT -> {
                Form cur = forms.current();
                if (!cur.isBase()) {
                    forms.enter(mob, Forms.byId(cur.parent()), now);
                    AiEvents.onTransform(mob, forms.form);
                    formNote = "reverted from " + cur.id() + ": " + why;
                    note(formNote);
                }
            }
            case CHARGE_KI -> setPowering(true);
            default -> {}
        }
    }

    void setPowering(boolean on) {
        powering = on;
        mob.setAiFlag(KiFighter.AI_CHARGING, on || ki.charging != null);
        if (on) mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), com.dbzenith.registry.ModSounds.AURA_CHARGE.get(),
                net.minecraft.sounds.SoundSource.HOSTILE, 0.5f, 1.0f);
    }

    // ------------------------------------------------------------------ doing

    void execute(long now) {
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive()) {
            if (action != Action.CHARGE_KI && action != Action.IDLE) switchTo(Action.IDLE, "no foe", now);
            move.stop();
            if (move.flying && move.heightAboveGround() > 1.5) move.steer(mob.position().add(0, -3, 0), false, now);
            else move.land();
            return;
        }
        double dist = mob.distanceTo(t);
        updateFlight(t, dist, now);
        if (action != Action.KI_VOLLEY && action != Action.BIG_TECHNIQUE && ki.charging == null) mob.getLookControl().setLookAt(t, 30f, 30f);
        switch (action) {
            case IDLE -> move.stop();
            case APPROACH -> move.approach(t, MeleeTactics.REACH - 0.4, 1.15, flankAngle, now);
            case PRESSURE, PROTECT_ALLY -> closeAndStrike(t, dist, false, now);
            case PUNISH -> {
                dashIn(t, dist, now);
                closeAndStrike(t, dist, true, now);
            }
            case INTERRUPT -> {
                Technique q = ki.quick(now);
                if (dist > 6 && q != null && mob.hasLineOfSight(t)) {
                    if (ki.fire(q, t, 0, now)) note("interrupting a charge");
                } else {
                    dashIn(t, dist, now);
                    closeAndStrike(t, dist, true, now);
                }
            }
            case KEEP_DISTANCE -> {
                move.holdRange(t, 7, 13, now);
                Technique q = ki.quick(now);
                if (q != null && mob.hasLineOfSight(t) && roll(31, 0.35)) ki.fire(q, t, 0, now);
            }
            case STRAFE, EVADE -> move.strafe(t, now, 0.55f);
            case GUARD -> {
                move.stop();
                mob.lookAt(EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
                if (!res.guarding && now - guardRaisedAt > 4) raiseGuard(now);
                if (res.guarding && now - res.guardStartTick > 40 && now - res.lastGuardHit > 30) lowerGuard();   // no endless turtling
            }
            case KI_VOLLEY -> {
                Technique q = ki.quick(now);
                if (q != null && mob.hasLineOfSight(t)) ki.fire(q, t, 0, now);
                move.strafe(t, now, 0.4f);
            }
            case BIG_TECHNIQUE -> {
                if (ki.charging != null) {
                    move.stop();
                    move.hover(now);
                    ki.tickCharge(t, now);
                    break;
                }
                Technique b = ki.big(now);
                if (b == null) {
                    move.strafe(t, now, 0.4f);
                    break;
                }
                if (b.chargeTicks() > 0) {
                    int planned = (int) Math.min(KiTactics.MAX_CHARGE, Math.max(15, b.chargeTicks() * (0.25 + 0.35 * temper().patience) * (dist > 16 ? 1.3 : 1)));
                    ki.beginCharge(b, planned, now);
                    commitUntil = Math.max(commitUntil, now + planned + 2);
                    note("charging " + b.id());
                } else ki.fire(b, t, 0, now);
            }
            case CHARGE_KI -> {
                move.stop();
                move.hover(now);
                if (res.kiShare() > 0.85 || dist < 6) switchTo(Action.STRAFE, "charged up", now);
            }
            case TRANSFORM, REVERT -> move.stop();
            case RETREAT -> {
                if (Locomotion.mayFly(mob, this) && !move.flying && dist < 10) move.takeOff(now);
                move.retreatFrom(t, 18, now);
            }
            case PURSUE -> {
                if (mob instanceof BossFighter bf && dist > 14 && bf.aiVanishBehind(t)) note("vanished behind the foe");
                else move.approach(t, MeleeTactics.REACH - 0.4, 1.3, 0, now);
            }
        }
    }

    private void closeAndStrike(LivingEntity t, double dist, boolean finish, long now) {
        if (dist > MeleeTactics.REACH + t.getBbWidth() * 0.5) {
            move.approach(t, MeleeTactics.REACH - 0.5, finish ? 1.35 : 1.2, action == Action.PRESSURE ? flankAngle : 0, now);
            return;
        }
        if (!move.flying) mob.getNavigation().stop();
        else move.hover(now);
        if (!melee.strike(t, now, finish) && action == Action.PRESSURE && res.staminaShare() < 0.1) switchTo(Action.STRAFE, "winded", now);
    }

    /** A burst of speed at an opening (stamina). */
    private void dashIn(LivingEntity t, double dist, long now) {
        if (dist < 4 || dist > 10 || now < dashReadyAt || !res.spendStamina(8, now)) return;
        dashReadyAt = now + 20;
        Vec3 to = t.position().subtract(mob.position());
        Vec3 push = (mob.onGround() && !move.flying ? new Vec3(to.x, 0, to.z) : to).normalize().scale(1.1);
        if (!move.flying && !move.safeStep(mob.position().add(push.scale(2)))) return;
        mob.setDeltaMovement(push.add(0, mob.onGround() ? 0.1 : 0, 0));
        mob.hasImpulse = true;
    }

    /** Takes off when the fight is in the air, comes down when it is on the ground. */
    private void updateFlight(LivingEntity t, double dist, long now) {
        boolean may = Locomotion.mayFly(mob, this);
        boolean foeUp = !t.onGround() && t.getY() - move.groundBelow(t.position()) > 2.5;
        boolean above = t.getY() - mob.getY() > 3;
        boolean want = may && (foeUp || above && mob.getNavigation().isDone() && dist > 4
                || action == Action.PURSUE && dist > 20 || action == Action.RETREAT && dist < 10);
        if (want && !move.flying) {
            move.takeOff(now);
            note("took to the air");
        } else if (move.flying && (!may || !want && t.onGround() && move.heightAboveGround() < 2.5)) {
            move.land();
        }
    }

    // ------------------------------------------------------------------ the reactive layer (exact timing, after a reaction delay)

    void reactive(long now) {
        LivingEntity t = mob.getTarget();
        if (reactAt >= 0 && now >= reactAt) {
            int what = react;
            reactAt = -1;
            react = 0;
            doReaction(what, t, now);
        }
        if (reactAt >= 0 || t == null) return;
        if (Evasion.counterReady(mob, now) && roll(41, 0.4 + 0.5 * style.counter)) {           // after a vanish or a parry: the counter
            schedule(3, Math.max(2, reaction() / 2), now);
            return;
        }
        if (incomingEta >= 0 && now >= sidestepReadyAt && (action == Action.EVADE || roll(42, 0.25 * quality()))) {
            int delay = reaction();
            if (delay < incomingEta) schedule(4, delay, now);
            return;
        }
        Fighter g = CombatEngine.peek(t);
        int windup = g == null ? -1 : g.windupLeft();
        if (windup < 0 || mob.distanceTo(t) > 4.5 || !facing(t)) return;
        int key = g.move().id.hashCode() * 31 + (int) (now - g.moveTick());
        if (key == reactedToMove) return;
        reactedToMove = key;
        int delay = reaction();
        if (delay > windup) {                                                       // too late to answer: only a guard already up helps
            return;
        }
        boolean vanish = action == Action.EVADE && now >= vanishReadyAt && res.ki >= vanishCost();
        if (vanish) schedule(2, Math.max(delay, windup - 1), now);
        else if (action == Action.GUARD || roll(43, style.guard * quality())) {
            boolean tryParry = roll(44, 0.7 * quality() * style.counter);
            schedule(tryParry ? 1 : 5, tryParry ? Math.max(delay, windup - 1) : delay, now);
        }
    }

    private boolean facing(LivingEntity t) {
        Vec3 look = t.getLookAngle().multiply(1, 0, 1);
        Vec3 to = mob.position().subtract(t.position()).multiply(1, 0, 1);
        return look.lengthSqr() > 1e-4 && to.lengthSqr() > 1e-4 && look.normalize().dot(to.normalize()) > 0.5;
    }

    private void schedule(int what, int delay, long now) {
        react = what;
        reactAt = now + Math.max(1, delay);
    }

    private void doReaction(int what, LivingEntity t, long now) {
        switch (what) {
            case 1 -> {                                                            // a fresh guard right before the blow: a parry
                lowerGuard();
                raiseGuard(now);
            }
            case 5 -> raiseGuard(now);
            case 2 -> {                                                            // vanish: the blow will miss
                if (res.spendKi(vanishCost(), now)) {
                    lowerGuard();
                    int window;
                    try {
                        window = DBZConfig.SERVER.vanishWindowTicks.get() + 2;
                    } catch (IllegalStateException e) {
                        window = 7;
                    }
                    Evasion.openVanish(mob, window);
                    try {
                        vanishReadyAt = now + DBZConfig.SERVER.vanishCooldownTicks.get();
                    } catch (IllegalStateException e) {
                        vanishReadyAt = now + 60;
                    }
                }
            }
            case 3 -> {                                                            // the counter
                if (t != null && Evasion.counterReady(mob, now)) {
                    lowerGuard();
                    mob.lookAt(EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
                    CombatEngine.press(mob, new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, mob.onGround()));
                    note("countered");
                }
            }
            case 4 -> sidestep(now);
            default -> {}
        }
    }

    /** Out of the way of a ki attack: a dash across its path (stamina), the side with room. */
    void sidestep(long now) {
        if (incomingEta < 0 || !res.spendStamina(6, now)) return;
        sidestepReadyAt = now + 16;
        Vec3 side = new Vec3(-incomingDir.z, 0, incomingDir.x);
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        double strength;
        try {
            strength = DBZConfig.SERVER.dashStrength.get() * 0.55;
        } catch (IllegalStateException e) {
            strength = 0.9;
        }
        Vec3 a = side.scale(strength), b = side.scale(-strength);
        Vec3 pick = AiRandom.chance(seed, counter, 51, 0.5) ? a : b;
        if (!move.flying && !move.safeStep(mob.position().add(pick.scale(2)))) pick = pick == a ? b : a;
        if (!move.flying && !move.safeStep(mob.position().add(pick.scale(2)))) return;
        if (move.flying) pick = pick.add(0, 0.25, 0);
        mob.setDeltaMovement(pick.add(0, mob.onGround() ? 0.12 : 0, 0));
        mob.hasImpulse = true;
        incomingEta = -1;
        note("sidestepped ki");
    }

    // ------------------------------------------------------------------ allies

    void refreshSquad(long now, boolean force) {
        if (!force && now - squadAt < 40) return;
        squadAt = now;
        squad.clear();
        allyInDanger = null;
        allyAttacker = null;
        squadRetreat = false;
        for (KiFighter k : mob.level().getEntitiesOfClass(KiFighter.class, mob.getBoundingBox().inflate(20),
                k -> k != mob && k.isAlive() && Faction.allied(mob, k) && !KnockOut.isDown(k))) {
            squad.add(k);
            if (k.brainOrNull() != null && k.brainOrNull().action == Action.RETREAT && k.getTarget() == mob.getTarget()) squadRetreat = true;
            int by = AiEvents.lastAttacker(k, now, 60);
            if (by >= 0 && Power.health(k) < 0.4 && mob.level().getEntity(by) instanceof LivingEntity a && !Faction.allied(mob, a)
                    && (allyInDanger == null || Power.health(k) < Power.health(allyInDanger))) {
                allyInDanger = k;
                allyAttacker = a;
            }
        }
        // roles round the shared foe: the lowest id leads in, the others flank, every other one with ki supports from range
        LivingEntity foeNow = mob.getTarget();
        int index = 0;
        for (KiFighter k : squad) if (k.getTarget() == foeNow && foeNow != null && k.getId() < mob.getId()) index++;
        boolean hasKi = !mob.techniques().isEmpty();
        rangedRole = hasKi && index > 0 && index % 2 == 1;
        flankAngle = index == 0 ? 0 : (index % 2 == 1 ? 65 : -65) * ((index + 1) / 2);
        if (foeNow != null && !squad.isEmpty()) {                                  // the whole squad outmatched: fall back together
            double ours = Power.standing(mob);
            for (KiFighter k : squad) if (k.getTarget() == foeNow) ours += Power.standing(k);
            if (ours < Power.standing(foeNow) * 0.35 && temper().courage < 0.85) squadRetreat = true;
        }
    }

    // ------------------------------------------------------------------ events

    void onHurt(Entity by, double share, long now) {
        log.taken(share);
        lastFoughtTick = now;
        if (forms.transforming()) {
            double cut;
            try {
                cut = DBZConfig.SERVER.transformInterruptDamage.get();
            } catch (IllegalStateException e) {
                cut = 0.03;
            }
            if (share >= cut) {
                forms.stop();
                mob.setAiFlag(KiFighter.AI_TRANSFORMING, false);
                formNote = "power-up broken by a blow";
                note(formNote);
                commitUntil = now;
            }
        }
        if (ki.charging != null && share >= 0.04) {
            ki.fizzle(true);
            commitUntil = now;
        }
        if (powering && share > 0) {
            setPowering(false);
            commitUntil = now;
        }
    }

    void observedKi(String technique, long now) {
        foe.onKi(technique);
        if (technique != null && !technique.isEmpty() && seenTechniques.add(technique) && seenTechniques.size() > 1) {
            foeNewTechAt = now;
            note("new technique seen: " + technique);
        }
    }

    void observedTransform(String form, long now) {
        if (form.equals(foeForm)) return;
        foeForm = form;
        foe.onTransform();
        foeTransformedAt = now;
        nextDecision = Math.min(nextDecision, now + reaction());                   // reconsider, after its reaction time
        note("foe transformed: " + form);
    }

    /** A player's form is read when the foe is first seen and on every decision (transformations seen as they happen). */
    private void observeForm(LivingEntity t, long now) {
        String form = t instanceof Player p ? ModCapabilities.get(p).map(PlayerData::getFormId).orElse("base")
                : t instanceof KiFighter k ? k.aiFormId() : "base";
        if (foeForm.isEmpty()) foeForm = form;
        else if (!form.equals(foeForm)) observedTransform(form, now);
    }

    void allyFell(long now) {
        allyFellAt = now;
        note(temper().courage < 0.4 ? "an ally fell: shaken" : temper().aggression > 0.6 ? "an ally fell: furious" : "an ally fell");
        nextDecision = Math.min(nextDecision, now + reaction());
    }

    void noteUse(Technique t) {
        AiEvents.onKi(mob, t.id());
    }

    private void newFoe(LivingEntity t, long now) {
        if (foeId != null) endFight(null, now);
        foeId = t.getUUID();
        foeName = t.getName().getString();
        foe = new OpponentProfile();
        foeForm = "";
        seenTechniques.clear();
        fightStart = now;
        lowHealthReacted = false;
        fights++;
        log.clear();
        if (remembers()) {
            AiMemory mem = AiMemory.get(mob.getServer());
            AiMemory.Npc n = mem.npc(mob.getUUID(), typeId(), now);
            AiMemory.Opponent o = n.opponent(foeId, foeName, now);
            log.seed(o.payoff, o.tries);
            if (o.fights > 0) note("remembers " + foeName + " (" + o.wins + "-" + o.losses + ")");
        }
        com.dbzenith.nemesis.Nemesis.onFightStart(mob, t, foe);                     // a named character remembers the meeting
    }

    boolean remembers() {
        if (!profile.important() || mob.getServer() == null) return false;
        try {
            return DBZConfig.SERVER.aiMemory.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    String typeId() {
        var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return key == null ? "" : key.toString();
    }

    /** The fight with the current foe is over (won, lost, fled, or simply ended): what it taught is kept. */
    void endFight(Outcome how, long now) {
        if (foeId == null) return;
        log.switchTo(Action.IDLE);
        if (remembers()) {
            AiMemory mem = AiMemory.get(mob.getServer());
            AiMemory.Npc n = mem.npc(mob.getUUID(), typeId(), now);
            AiMemory.Opponent o = n.opponent(foeId, foeName, now);
            o.fights++;
            if (how == Outcome.WON) o.wins++;
            if (how == Outcome.LOST) {
                o.losses++;
                o.grudge = Math.min(10, o.grudge + 1);
                n.fallen = true;
            }
            if (how == Outcome.FLED) o.fled++;
            mem.learn(n, o, log);
        }
        com.dbzenith.nemesis.Nemesis.onFightEnd(mob, foeId, foe, how);
        if (how != null) note("fight " + how.name().toLowerCase() + " vs " + foeName);
        foeId = null;
        fightStart = -1;
    }

    // ------------------------------------------------------------------ saving

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("personality", personality.id());
        t.putInt("fights", fights);
        t.put("pools", res.save());
        t.put("forms", forms.save());
        t.putBoolean("flying", move.flying);
        return t;
    }

    public void load(CompoundTag t) {
        Personality p = Personality.byId(t.getString("personality"));
        if (p != null) setPersonality(p);
        fights = t.getInt("fights");
        res.setLevel(mob.fighterLevel());
        res.load(t.getCompound("pools"));
        forms.load(t.getCompound("forms"));
        wasFlyingOnLoad = t.getBoolean("flying");
    }

    // ------------------------------------------------------------------ tests

    /** Tests: raise its guard now. */
    public void testRaiseGuard(long now) {
        raiseGuard(now);
    }

    /** Tests: whether an ally stands between it and {@code foe} (its squad looked up now). */
    public boolean testAllyInLine(LivingEntity foe) {
        refreshSquad(mob.level().getGameTime(), true);
        return ki.allyInLine(foe);
    }

    /** Tests: take a form at once. */
    public void testEnterForm(Form f) {
        forms.enter(mob, f, mob.level().getGameTime());
    }

    /** Tests: what it would see now. */
    public Situation testPerceive(LivingEntity t) {
        return perceive(t, mob.level().getGameTime());
    }

    // ------------------------------------------------------------------ debug

    /** One line for the action bar (/dbz ai watch). */
    public String line() {
        LivingEntity t = mob.getTarget();
        return String.format("%s [%s] %s -> %s | ki %d%% st %d%% guard %d%% | %s%s", mob.getName().getString(), personality.id(), action.id(),
                t == null ? "-" : t.getName().getString(), Math.round(res.kiShare() * 100), Math.round(res.staminaShare() * 100),
                Math.round(res.guardShare() * 100), forms.form, move.flying ? " (flying)" : "");
    }

    /** The full readout (/dbz ai inspect). */
    public List<String> describe() {
        long now = mob.level().getGameTime();
        List<String> out = new ArrayList<>();
        LivingEntity t = mob.getTarget();
        out.add(mob.getName().getString() + " lv " + mob.fighterLevel() + " | " + personality.id() + ", " + style.name().toLowerCase() + " style, "
                + tier.name().toLowerCase() + " AI (reacts in " + reaction() + " ticks, decides every " + decisionInterval() + "), "
                + profile.faction().name().toLowerCase() + ", fights " + fights);
        out.add((now - lastActiveTick > 1 ? "not fighting; last action " : "action ") + action.id() + (actionSince > 0 ? " (" + (now - actionSince) + " ticks)" : "") + (reason.isEmpty() ? "" : " because " + reason));
        out.add("target " + (t == null ? "none" : t.getName().getString() + String.format(" at %.1f, threat %.2f, ratio %.2f", mob.distanceTo(t),
                ThreatAssessment.score(mob, this, t, now), Power.standing(mob) / Math.max(1, Power.standing(t)))));
        out.add(String.format("ki %.0f/%.0f, stamina %.0f, guard %.0f%s%s%s", res.ki, res.maxKi, res.stamina, res.guard, res.guarding ? " (up)" : "",
                powering ? ", charging ki" : "", move.flying ? ", flying" : ""));
        out.add("form " + forms.form + (forms.transforming() ? " -> " + forms.target + " (" + forms.ticks + "/" + forms.total + ")" : "")
                + " | ladder " + NpcForms.ladder(profile, Power.equivalentLevel(mob.fighterLevel())).stream().map(Form::id).toList()
                + (formNote.isEmpty() ? "" : " | " + formNote));
        out.add("opponent: " + foe.summary());
        out.add("learned: " + log.summary());
        if (lastScores != null) {
            StringBuilder sb = new StringBuilder("scores:");
            for (int k = 0; k < 4; k++) {
                Action a = lastScores.ranked(k);
                if (lastScores.of(a) == UtilityScorer.NO) break;
                sb.append(String.format(" %s %.2f", a.id(), lastScores.of(a)));
            }
            out.add(sb.toString());
        }
        if (!squad.isEmpty()) out.add("squad " + squad.size() + (rangedRole ? ", ranged support" : "") + (flankAngle != 0 ? ", flank " + (int) flankAngle : "")
                + (allyAttacker != null ? ", protecting from " + allyAttacker.getName().getString() : "") + (squadRetreat ? ", falling back" : ""));
        out.add("recent: " + String.join(" | ", notes));
        return out;
    }
}
