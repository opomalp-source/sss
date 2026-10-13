package com.dbzenith.client.hair;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.HairCode.Preset;
import com.dbzenith.appearance.HairCode.Strand;
import com.dbzenith.transform.Form;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * From a character's hair code to the hair drawn: a stock style (the code of a {@link Preset}, or an old saved one that
 * upgrades to it) takes its sculpted look for the form ({@link HairStyles}); hair drawn in the editor becomes a lock per
 * strand, raised for a form the way the code says ({@link HairCode#forForm}).
 */
public final class HairCodes {
    private static final Map<String, Preset> STOCK = new HashMap<>();
    private static final Map<String, HairShape> DRAWN = new LinkedHashMap<>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, HairShape> e) {
            return size() > 128;
        }
    };

    static {
        for (Preset p : Preset.values()) if (p != Preset.BALD) STOCK.put(p.code(), p);
    }

    private HairCodes() {}

    /** The stock style a hair code is (bald for none), or null for hair drawn in the editor. */
    public static Preset presetOf(String code) {
        if (code == null || code.isEmpty()) return Preset.BALD;
        Preset p = STOCK.get(code);
        if (p != null) return p;
        String canonical = HairCode.sanitize(code);
        return canonical == null ? null : canonical.isEmpty() ? Preset.BALD : STOCK.get(canonical);
    }

    /** The hair a character with this code has in a form. */
    public static HairShape shape(String code, Form form) {
        Preset p = presetOf(code);
        if (p != null) return HairStyles.of(p, HairLook.of(form));
        return drawn(form.hairStyle() == Form.HairStyle.NONE ? code : HairCode.forForm(code, form));
    }

    /** Part of the way from one form's hair to another's. */
    public static HairShape between(String code, Form from, Form to, float progress) {
        Preset p = presetOf(code);
        if (p != null) return HairStyles.between(p, HairLook.of(from), HairLook.of(to), progress);
        HairShape a = shape(code, from), b = shape(code, to);
        if (a == b || progress <= 0) return a;
        if (progress >= 1) return b;
        int step = Math.max(1, Math.min(31, Math.round(progress * 32)));
        synchronized (DRAWN) {
            return DRAWN.computeIfAbsent(code + "|" + from.id() + ">" + to.id() + "|" + step, k -> HairShape.morph(a, b, step / 32f));
        }
    }

    /** Editor hair: a lock per strand, over a scalp when the strands cover the crown. */
    static HairShape drawn(String code) {
        synchronized (DRAWN) {
            HairShape cached = DRAWN.get(code);
            if (cached != null) return cached;
        }
        List<Strand> strands = HairCode.decode(code);
        HairShape out;
        if (strands == null || strands.isEmpty()) out = HairShape.BALD;
        else {
            Sculpt s = new Sculpt();
            if (HairCode.hasVolume(code)) s.cap(-7.4, 0, .3, -5.4, -4);
            for (Strand st : strands) strand(s, st);
            out = s.build();
        }
        synchronized (DRAWN) {
            DRAWN.put(code, out);
        }
        return out;
    }

    private static void strand(Sculpt s, Strand st) {
        float a = st.u() + .5f, b = st.v() + .5f;
        double[] root, out, right, down;
        switch (st.face()) {
            case TOP -> { root = new double[]{-4 + a, -8, -4 + b}; out = new double[]{0, -1, 0}; right = new double[]{1, 0, 0}; down = new double[]{0, 0, 1}; }
            case FRONT -> { root = new double[]{-4 + a, -8 + b, -4}; out = new double[]{0, 0, -1}; right = new double[]{1, 0, 0}; down = new double[]{0, 1, 0}; }
            case BACK -> { root = new double[]{4 - a, -8 + b, 4}; out = new double[]{0, 0, 1}; right = new double[]{-1, 0, 0}; down = new double[]{0, 1, 0}; }
            case LEFT -> { root = new double[]{4, -8 + b, -4 + a}; out = new double[]{1, 0, 0}; right = new double[]{0, 0, 1}; down = new double[]{0, 1, 0}; }
            default -> { root = new double[]{-4, -8 + b, 4 - a}; out = new double[]{-1, 0, 0}; right = new double[]{0, 0, -1}; down = new double[]{0, 1, 0}; }
        }
        double yaw = Math.toRadians(st.yaw() * 15), pitch = Math.toRadians(st.pitch() * 15);
        double[] d = new double[3];
        for (int c = 0; c < 3; c++) d[c] = out[c] * Math.cos(pitch) * Math.cos(yaw) + right[c] * Math.sin(yaw) * Math.cos(pitch) - down[c] * Math.sin(pitch);
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -d[1] / l)))), az = Math.toDegrees(Math.atan2(d[0], d[2]));
        double curl = switch (st.bend()) {
            case HANG -> -70;
            case DROOP -> -30;
            case LIFT -> 16;
            default -> 0;
        };
        s.feel(st.bend() == HairCode.Bend.HANG ? .22f : .55f, 1);
        s.lock(root[0] - out[0] * .15, root[1] - out[1] * .15, root[2] - out[2] * .15, el, az, st.length(), st.width() * 1.1, curl, 0, .6, 1);
    }
}
