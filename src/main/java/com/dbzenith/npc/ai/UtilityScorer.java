package com.dbzenith.npc.ai;

/**
 * The heart of the decision: every {@link Action} gets a utility from the {@link Situation}, a sum of named terms
 * (health, ki, stamina, relative power, distance, what the opponent is doing and tends to do, the techniques ready,
 * forms, personality, style, allies), plus what the fighter has learned it pays ({@link FightLog#bias}) and a small
 * bonus for keeping what it is doing. Actions that make no sense now are {@link #NO}. A pure function: the same
 * situation always scores the same, so tests can check the choices directly, and the debug readout can show the terms
 * behind a choice.
 */
public final class UtilityScorer {
    public static final double NO = Double.NEGATIVE_INFINITY;
    /** Bonus for the action under way (hysteresis). */
    public static final double KEEP = 0.12;

    private UtilityScorer() {}

    /** Scores, and the terms behind each, for one decision. */
    public static final class Result {
        public final double[] score = new double[FightLog.N];
        final StringBuilder[] why = new StringBuilder[FightLog.N];

        public double of(Action a) {
            return score[a.ordinal()];
        }

        /** The best action (ties go to the earlier one: deterministic). */
        public Action best() {
            return ranked(0);
        }

        /** The {@code n}th best (0 = best) of the actions that make sense, or the best if there are fewer. */
        public Action ranked(int n) {
            Action[] all = Action.values();
            boolean[] used = new boolean[all.length];
            Action pick = Action.IDLE;
            for (int k = 0; k <= n; k++) {
                Action best = null;
                double bs = NO;
                for (Action a : all) {
                    double s = score[a.ordinal()];
                    if (!used[a.ordinal()] && s > bs) {
                        bs = s;
                        best = a;
                    }
                }
                if (best == null) return pick;
                used[best.ordinal()] = true;
                pick = best;
            }
            return pick;
        }

        /** The terms that decided an action's score, largest first ("close +0.25, aggression +0.21"). */
        public String why(Action a) {
            StringBuilder sb = why[a.ordinal()];
            return sb == null || sb.length() == 0 ? "-" : sb.toString();
        }
    }

    private static final class Terms {
        final java.util.List<String> names = new java.util.ArrayList<>();
        final java.util.List<Double> values = new java.util.ArrayList<>();
        double v;

        Terms add(String name, double x) {
            v += x;
            if (Math.abs(x) >= 0.03) {
                names.add(name);
                values.add(x);
            }
            return this;
        }

        String text() {
            Integer[] idx = new Integer[names.size()];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            java.util.Arrays.sort(idx, (a, b) -> Double.compare(Math.abs(values.get(b)), Math.abs(values.get(a))));
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < Math.min(4, idx.length); k++) {
                if (k > 0) sb.append(", ");
                sb.append(names.get(idx[k])).append(String.format(" %+.2f", values.get(idx[k])));
            }
            return sb.toString();
        }
    }

    public static Result score(Situation s) {
        Result r = new Result();
        for (Action a : Action.values()) {
            Terms t = new Terms();
            double base = score(a, s, t);
            if (base == NO) {
                r.score[a.ordinal()] = NO;
                continue;
            }
            double b = s.bias[a.ordinal()];
            if (b != 0) t.add("learned", b);
            if (s.current == a) t.add("keep", KEEP);
            r.score[a.ordinal()] = t.v;
            r.why[a.ordinal()] = new StringBuilder(t.text());
        }
        return r;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** One action's score, its terms added to {@code t} (returns NO when it makes no sense). */
    static double score(Action a, Situation s, Terms t) {
        Temper p = s.temper();
        FightStyle st = s.style;
        OpponentProfile pr = s.profile;
        double d = s.distance;
        boolean close = d <= 3.5;
        double lr = clamp(Math.log(Math.max(1e-3, s.ratio)), -2, 2);
        boolean ranged = !s.meleeOnly;
        if (!s.hasTarget) {
            switch (a) {
                case IDLE -> t.add("no foe", 0.5);
                case REVERT -> {
                    if (!s.inForm) return NO;
                    t.add("fight over", 0.9);
                }
                case CHARGE_KI -> {
                    if (s.ki > 0.9) return NO;
                    t.add("recover", 0.2 + (0.9 - s.ki) * 0.4);
                }
                default -> { return NO; }
            }
            return t.v;
        }
        switch (a) {
            case IDLE -> t.add("idle", 0.0);
            case APPROACH -> {
                if (close) return NO;
                t.add("base", 0.30).add("melee style", 0.30 * st.melee).add("aggression", 0.15 * p.aggression)
                        .add("they keep away", 0.25 * pr.kiter());
                if (ranged) t.add("prefers range", -0.25 * (1 - st.melee));
                t.add("danger", -0.2 * p.caution * (clamp(-lr, 0, 2) * 0.5 + (1 - s.health) * 0.6));
                if (s.foeAirborne && !s.canFly && !s.airborne) t.add("can't reach", -0.4);
            }
            case PRESSURE -> {
                if (d > 7 || s.foeDowned) return NO;
                t.add("base", 0.40).add("melee style", 0.35 * st.melee).add("aggression", 0.25 * p.aggression)
                        .add("range", close ? 0.25 : -0.06 * (d - 3.5)).add("power", 0.12 * lr).add("stamina", (s.stamina - 0.35) * 0.6);
                if (s.foeStunned) t.add("combo", 0.35);
                if (s.foeGuarding) t.add("they guard", -0.15 * (1 - pr.turtle() * 0.5));
                t.add("they out-melee", -0.2 * p.caution * pr.meleeHeavy());
                if (s.foeAirborne && !s.canFly && !s.airborne) t.add("can't reach", -0.5);
                if (s.stamina < 0.12) t.add("winded", -0.6);
            }
            case PUNISH -> {
                if (!(s.foeRecovering || s.foeExhausted) || d >= 9 || s.foeDowned) return NO;
                t.add("opening", 0.85).add("quality", 0.25 * s.quality).add("aggression", 0.1 * p.aggression);
                if (s.stamina < 0.2) t.add("winded", -0.5);
            }
            case INTERRUPT -> {
                if (!s.foeCharging || d >= 26 || !(s.quickReady || d < 12) || s.foeDowned) return NO;
                t.add("they charge", 0.7).add("they keep charging", 0.3 * pr.charger()).add("aggression", 0.2 * p.aggression).add("quality", 0.15 * s.quality);
                if (s.ratio < 0.4) t.add("too strong", -0.25 * p.caution);
            }
            case KEEP_DISTANCE -> {
                if (!ranged) {
                    if (p.kind != Personality.COWARDLY) return NO;
                    t.add("cowardice", 0.2);
                    break;
                }
                t.add("base", 0.20).add("ki style", 0.45 * (1 - st.melee)).add("caution", 0.2 * p.caution)
                        .add("they melee", 0.35 * pr.meleeHeavy()).add("they rush", 0.15 * pr.rusher()).add("aggression", -0.15 * p.aggression);
                if (s.ratio < 0.8) t.add("outmatched", 0.12);
                if (d < 5) t.add("too close", 0.12);
                if (s.ki < 0.15) t.add("no ki", -0.35);
                if (s.rangedRole) t.add("support role", 0.2);
            }
            case STRAFE -> {
                if (d < 3 || d > 14) return NO;
                t.add("base", 0.15).add("patience", 0.2 * p.patience).add("dodge their blasts", 0.3 * pr.blastSpam()).add("aggression", -0.15 * p.aggression);
                if (st == FightStyle.TACTICAL) t.add("tactics", 0.1);
                if (s.foeGuarding) t.add("wait out the guard", 0.1);
            }
            case GUARD -> {
                if (d > 5 || s.guard < 0.2) return NO;
                t.add("base", 0.10).add("guard style", 0.3 * st.guard).add("caution", 0.2 * p.caution).add("they melee", 0.25 * pr.meleeHeavy())
                        .add("recklessness", -0.25 * p.recklessness);
                if (s.foeAttacking) t.add("blow coming", 0.5);
                if (s.incoming >= 0 && s.incoming < 10) t.add("ki coming", 0.25);
                if (s.foeGuarding) t.add("both guarding", -0.25);
                if (s.guard < 0.35) t.add("guard worn", -0.4);
            }
            case EVADE -> {
                if (s.incoming >= 0 && s.incoming <= 14 && s.stamina > 0.1) {
                    t.add("ki coming", 0.8).add("quality", 0.25 * s.quality).add("they spam ki", 0.2 * pr.blastSpam()).add("recklessness", -0.2 * p.recklessness);
                } else if (s.foeAttacking && d <= 4 && s.vanishReady) {
                    t.add("blow coming", 0.35).add("quality", 0.35 * s.quality).add("they melee", 0.25 * pr.meleeHeavy()).add("counter style", 0.2 * st.counter)
                            .add("recklessness", -0.15 * p.recklessness);
                } else return NO;
            }
            case KI_VOLLEY -> {
                if (!ranged || !s.quickReady || !s.lineOfSight || d < 3) return NO;
                t.add("base", 0.30).add("ki style", 0.35 * (1 - st.melee)).add("they turtle", 0.1 * pr.turtle()).add("they dodge", -0.2 * pr.dodgy())
                        .add("ki", (s.ki - 0.5) * 0.2).add("they keep away", 0.1 * pr.kiter());
                if (d > 8) t.add("range", 0.25);
                if (s.foeAirborne && !s.canFly && !s.airborne) t.add("can't reach", 0.35);
                if (s.rangedRole) t.add("support role", 0.25);
                if (s.ki < 0.12) t.add("low ki", -0.4);
            }
            case BIG_TECHNIQUE -> {
                if (!ranged || !s.bigReady || !s.lineOfSight || d < 5 || d > 32) return NO;
                t.add("base", 0.20).add("ki", (s.ki - 0.45) * 0.6).add("they turtle", 0.25 * pr.turtle()).add("they dodge", -0.25 * pr.dodgy())
                        .add("aggression", 0.1 * p.aggression).add("recklessness", 0.1 * p.recklessness);
                if (s.foeHealth < 0.35) t.add("finish", 0.25);
                if (s.foeStunned) t.add("can't dodge", 0.3);
                if (s.foeCharging) t.add("they charge", 0.2);
                if (d < 7) t.add("too close", -0.15);
                if (s.boss) t.add("boss", 0.1);
            }
            case CHARGE_KI -> {
                if (s.ki >= 0.5 || s.meleeOnly && !s.canFly && !s.canTransformUp && !s.inForm) return NO;
                t.add("low ki", 0.15 + (0.5 - s.ki) * 1.4).add("patience", 0.15 * p.patience).add("discipline", 0.1 * p.discipline);
                if (d < 9) t.add("too close", -0.6);
                else if (d < 14) t.add("close", -0.2);
                if (s.foeAttacking) t.add("blow coming", -0.3);
                if (s.incoming >= 0) t.add("ki coming", -0.5);
            }
            case TRANSFORM -> {
                if (!s.canTransformUp) return NO;
                double desired = 1.0 + 0.35 * p.caution - 0.3 * p.recklessness;
                double need = clamp(desired - s.ratio, 0, 1.5) + (s.foeJustTransformed ? 0.3 : 0);
                if (need < 0.12 && (s.health > 0.5 || s.ratio > 1.5)) return NO;   // escalate only when needed (never when well ahead)
                t.add("outmatched", 0.15 + need * 0.9).add("caution", 0.1 * p.caution).add("they transform", 0.1 * pr.transformer());
                if (s.health < 0.5) t.add("hurt", 0.25);
                if (s.health < 0.3) t.add("badly hurt", 0.2);
                if (s.foeJustTransformed) t.add("they transformed", 0.2);
                t.add("pride", -0.55 * p.pride * (s.health > 0.55 ? 1 : s.health > 0.35 ? 0.4 : 0));
                if (s.kiAfterForm < 0.1) t.add("can't afford", -1.0);
                else if (s.kiAfterForm < 0.25) t.add("costly", -0.3);
                if (d < 4) t.add("too close", -0.2);
                if (s.incoming >= 0) t.add("ki coming", -0.3);
            }
            case REVERT -> {
                if (!s.inForm) return NO;
                if (s.ki < 0.15) t.add("out of ki", 0.5 + 0.3 * p.discipline);
                else if (s.ratio > 2.2 && p.discipline > 0.55) t.add("not needed", 0.35);
                else return NO;
            }
            case RETREAT -> {
                if (s.noRetreat) return NO;
                t.add("hurt", 0.05 + (1 - s.health) * (1 - p.courage) * 1.1).add("outmatched", clamp(-lr, 0, 2) * 0.3 * (1 - p.courage))
                        .add("recklessness", -0.4 * p.recklessness);
                if (s.squadRetreat) t.add("squad falls back", 0.5);
                if (s.health < 0.25 && p.courage < 0.3) t.add("fear", 0.4);
                if (s.boss) t.add("boss", -0.35);
                if (s.allyInDanger) t.add("ally needs me", -0.2 * p.honor);
                if (s.ki < 0.15 && s.stamina < 0.2) t.add("spent", 0.25);
            }
            case PURSUE -> {
                if (d <= 12 && s.lineOfSight) return NO;
                t.add("base", 0.30).add("aggression", 0.3 * p.aggression).add("they keep away", 0.3 * pr.kiter());
                if (s.foeHealth < 0.3) t.add("finish", 0.2);
                if (d > 20) t.add("far", 0.15);
                if (s.ratio < 0.5) t.add("too strong", -0.3 * p.caution);
            }
            case PROTECT_ALLY -> {
                if (!s.allyInDanger) return NO;
                t.add("ally in danger", 0.55).add("honor", 0.35 * p.honor).add("courage", 0.1 * p.courage);
                if (s.health < 0.3) t.add("hurt", -0.3);
            }
        }
        return t.v;
    }
}
