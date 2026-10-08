package com.dbzenith.combat.meter;

import com.dbzenith.data.PlayerData;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.Kaioken;
import com.dbzenith.transform.UltraInstinct;

import java.util.ArrayList;
import java.util.List;

/**
 * The PvP meters (CX-20): two bars of 0..100 shown bottom right in PvP mode.
 * <ul>
 *   <li><b>The form meter</b> (right): its steps are the forms you have unlocked, in the order J takes you through them,
 *       spread evenly up the bar (two forms: 50 and 100).</li>
 *   <li><b>The technique meter</b> (left): Kaioken's stages you have learned (x2 at 25, x4 at 40, x10 at 60, x20 at 80)
 *       and Ultra Instinct at 100 (the Sign, or Mastered once mastered). Ultra Instinct is a technique here, not a form.</li>
 * </ul>
 * Both sides compute the steps from the player's data, so the client draws the same studs the server checks.
 */
public final class Meters {
    /** One stud on a bar: what it opens, where it sits (0..100), and its colour (RGB). */
    public record Step(String id, String nameKey, double at, int color) {}

    public static final int KAIOKEN = Kaioken.RED, UI_SIGN = 0xA9B1BD, UI_MASTERED = 0xF4F8FF;
    /** Kaioken's stages on the technique bar, with where each sits. */
    public static final int[] KAIOKEN_STAGES = {2, 4, 10, 20};
    public static final double[] KAIOKEN_AT = {25, 40, 60, 80};
    public static final double ULTRA_INSTINCT_AT = 100;

    private Meters() {}

    /** The forms on the form bar, from the first above base up: the way J climbs, each one unlocked. */
    public static List<Step> formSteps(PlayerData d) {
        List<Form> ladder = new ArrayList<>();
        Form f = Forms.BASE;
        Form target = Forms.exists(d.getTargetForm()) ? Forms.byId(d.getTargetForm()) : null;
        for (int guard = 0; guard < 16; guard++) {
            Form next = null;
            for (Form child : Forms.children(f.id(), d.getRace(), d.getVariant())) {
                if (isTechnique(child) || FormHandler.problem(d, child) != null) continue;
                if (target != null && target != child && !Forms.isOnPath(child.id(), target)) {
                    if (next == null) next = child;                                   // keep looking for the target's way
                    continue;
                }
                next = child;
                break;
            }
            if (next == null) break;
            ladder.add(next);
            f = next;
        }
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < ladder.size(); i++) {
            Form form = ladder.get(i);
            steps.add(new Step(form.id(), form.translationKey(), 100.0 * (i + 1) / ladder.size(), form.auraColor()));
        }
        return steps;
    }

    /** Kaioken's learned stages, then Ultra Instinct if you have it. */
    public static List<Step> techSteps(PlayerData d) {
        List<Step> steps = new ArrayList<>();
        int max = Kaioken.maxStage(d);
        for (int i = 0; i < KAIOKEN_STAGES.length; i++) {
            if (KAIOKEN_STAGES[i] > max) break;
            steps.add(new Step("kaioken_" + KAIOKEN_STAGES[i], "meter.dbzenith.kaioken_" + KAIOKEN_STAGES[i], KAIOKEN_AT[i], KAIOKEN));
        }
        if (d.hasFlag(UltraInstinct.MASTERED_FLAG)) {
            steps.add(new Step("ultra_instinct", Forms.ULTRA_INSTINCT.translationKey(), ULTRA_INSTINCT_AT, UI_MASTERED));
        } else if (d.hasFlag(UltraInstinct.SIGN_FLAG)) {
            steps.add(new Step("ultra_instinct_sign", Forms.ULTRA_INSTINCT_SIGN.translationKey(), ULTRA_INSTINCT_AT, UI_SIGN));
        }
        return steps;
    }

    /** Ultra Instinct sits on the technique bar, not the form bar. */
    public static boolean isTechnique(Form f) {
        return f == Forms.ULTRA_INSTINCT || f == Forms.ULTRA_INSTINCT_SIGN;
    }

    /** The last step reached at {@code value}, or null below the first. */
    public static Step reached(List<Step> steps, double value) {
        Step r = null;
        for (Step s : steps) if (value + 1e-6 >= s.at()) r = s;
        return r;
    }

    /** The bar's colour for the technique meter: the step reached, else the next one's (nothing yet: Kaioken red). */
    public static int techColor(List<Step> steps, double value) {
        Step r = reached(steps, value);
        if (r != null) return r.color();
        return steps.isEmpty() ? KAIOKEN : steps.get(0).color();
    }
}
