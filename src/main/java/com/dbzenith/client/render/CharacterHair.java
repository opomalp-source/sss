package com.dbzenith.client.render;

import com.dbzenith.appearance.HairCode.Preset;
import com.dbzenith.client.render.HairGeometry.Cap;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every stock hairstyle in every look ({@link HairLook}). The characters are sculpted after their show designs:
 * Goku's Super Saiyan fan, Vegeta's flame and widow's peak, Gohan's single Super Saiyan 2 bang and Beast crest,
 * Broly's towering Legendary mane, Raditz's knee-length hair, Future Trunks standing straight up. The other cuts turn
 * Super Saiyan the way their shape suggests: short hair spikes up and out, long hair stiffens and flares, and each
 * keeps what makes it itself. Super Saiyan 3 grows a mane to the waist; Super Saiyan 4 lets the own hair grow long
 * and wild; God slims it; Beast draws it up into a crest. Head pixels: -Z is the face, -Y is up, +X the left.
 */
public final class CharacterHair {
    private static final Map<Preset, EnumMap<HairLook, HairSculpt>> SCULPTS = new EnumMap<>(Preset.class);
    private static final Map<String, List<HairMesh.Segment>> MESHES = new LinkedHashMap<>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<HairMesh.Segment>> e) {
            return size() > 160;
        }
    };

    private CharacterHair() {}

    /** A hairstyle in a look, as locks. */
    static synchronized HairSculpt sculpt(Preset preset, HairLook look) {
        return SCULPTS.computeIfAbsent(preset, p -> new EnumMap<>(HairLook.class)).computeIfAbsent(look, l -> author(preset, l));
    }

    /** A hairstyle in a look, as blocks. */
    public static synchronized List<HairMesh.Segment> mesh(Preset preset, HairLook look) {
        return MESHES.computeIfAbsent(preset + "|" + look, k -> sculpt(preset, look).build());
    }

    /** Part of the way from one look to another (64 steps, each built once). */
    public static synchronized List<HairMesh.Segment> transition(Preset preset, HairLook from, HairLook to, float progress) {
        if (from == to || progress <= 0) return mesh(preset, from);
        if (progress >= 1) return mesh(preset, to);
        int step = Math.max(1, Math.min(63, Math.round(progress * 64)));
        return MESHES.computeIfAbsent(preset + "|" + from + ">" + to + "|" + step,
                k -> HairSculpt.morph(sculpt(preset, from), sculpt(preset, to), step / 64f).build());
    }

    // ------------------------------------------------------------------ who wears what

    private static HairSculpt author(Preset preset, HairLook look) {
        if (preset == Preset.BALD) return look == HairLook.BASE ? new HairSculpt() : author(Preset.SPIKY, look);
        HairSculpt base = base(preset);
        return switch (look) {
            case BASE -> base;
            case GOD -> base.slimmed(.78, .9);
            case SSJ -> ssj(preset, base);
            case SSJ2 -> ssj2(preset, base);
            case SSJ3 -> ssj3(preset, base);
            case SSJ4 -> ssj4(preset, base);
            case BEAST -> beast(preset, base);
        };
    }

    /** The own hair. */
    static HairSculpt base(Preset preset) {
        return switch (preset) {
            case SPIKY -> HairSculpt.record(HairPresetLibrary::goku);
            case PRINCE -> HairSculpt.record(HairPresetLibrary::vegeta);
            case TEEN -> HairSculpt.record(h -> HairPresetLibrary.gohan(h, false));
            case GOHAN_ADULT -> HairSculpt.record(h -> HairPresetLibrary.gohan(h, true));
            case WILD -> HairSculpt.record(HairPresetLibrary::broly);
            case MANE -> HairSculpt.record(h -> { HairPresetLibrary.raditz(h); HairPresetLibrary.mane(h, 27, 1.05); });
            case CURTAINS -> trunks(new HairSculpt());
            case SAGE -> HairSculpt.record(h -> { HairPresetLibrary.curtains(h, true); HairPresetLibrary.mane(h, 13, .8); });
            case LONG -> HairSculpt.record(h -> { HairPresetLibrary.curtains(h, true); HairPresetLibrary.drape(h, 0, 11, 5); });
            case SWEPT -> HairSculpt.record(h -> HairPresetLibrary.swept(h, false));
            case SIDECUT -> HairSculpt.record(h -> HairPresetLibrary.swept(h, true));
            case SLICK -> HairSculpt.record(HairPresetLibrary::slick);
            case BOWL -> HairSculpt.record(HairPresetLibrary::bowl);
            case BUZZ -> HairSculpt.record(h -> h.cap(Cap.BUZZ));
            case PUFF -> HairSculpt.record(HairPresetLibrary::puff);
            case MOHAWK -> HairSculpt.record(HairPresetLibrary::mohawk);
            case PONYTAIL -> HairSculpt.record(h -> { HairPresetLibrary.slick(h); HairPresetLibrary.drape(h, 0, 12, 3); });
            case TWINTAILS -> HairSculpt.record(h -> { HairPresetLibrary.curtains(h, false); HairPresetLibrary.drape(h, -5, 11, 2); HairPresetLibrary.drape(h, 5, 11, 2); });
            case TOPKNOT -> HairSculpt.record(h -> { HairPresetLibrary.slick(h); HairPresetLibrary.lock(h, 0, -9, 2, 0, -12, 3, 3.4, 3, 3); });
            case BALD -> new HairSculpt();
        };
    }

    // ------------------------------------------------------------------ Super Saiyan

    private static HairSculpt ssj(Preset preset, HairSculpt base) {
        return switch (preset) {
            case SPIKY -> superFan(new HairSculpt(), 1, 1, 1, false);
            case TEEN -> superFan(new HairSculpt(), .92, .95, .9, false);
            case GOHAN_ADULT -> bang(superFan(new HairSculpt(), .78, .95, .85, false), -1.3);
            case WILD -> legendary(new HairSculpt(), 1.22);
            case PRINCE -> base.towering(1.12);
            case MANE -> raditzSsj(new HairSculpt(), 1);
            case CURTAINS -> superFan(new HairSculpt(), 1.05, .95, .5, false);
            default -> genericSsj(preset, base, false);
        };
    }

    private static HairSculpt ssj2(Preset preset, HairSculpt base) {
        return switch (preset) {
            case SPIKY -> bang(superFan(new HairSculpt(), 1.12, .88, .95, true), -1.2);
            case TEEN -> bang(superFan(new HairSculpt(), 1.05, .85, .72, true), -1.2);
            case GOHAN_ADULT -> bang(superFan(new HairSculpt(), .95, .88, .8, true), -1.3);
            case WILD -> legendary(new HairSculpt(), 1.4);
            case PRINCE -> base.towering(1.24).slimmed(.9, 1);
            case MANE -> raditzSsj(new HairSculpt(), 1.15);
            case CURTAINS -> bang(superFan(new HairSculpt(), 1.18, .88, .55, true), -1.2);
            default -> genericSsj(preset, base, true);
        };
    }

    private static HairSculpt ssj3(Preset preset, HairSculpt base) {
        HairSculpt s = switch (preset) {
            case PRINCE -> base.towering(1.1);                                    // the flame stays, a mane falls behind it
            case MANE -> raditzSsj(new HairSculpt(), 1.1);
            default -> crest(new HairSculpt());
        };
        s.cap(Cap.LONG);
        mane(s, preset == Preset.MANE ? 34 : 27, 1, -88);
        if (preset != Preset.PRINCE) s.spike(-1.1, -8.5, -3.8, -66, 182, 5.8, 2.2, -18, 0, .2);   // the one long bang
        return s;
    }

    private static HairSculpt ssj4(Preset preset, HairSculpt base) {
        HairSculpt s = switch (preset) {
            case PRINCE -> vegetaSsj4(new HairSculpt());
            case SPIKY -> base.scaled(1.16, 1.06);
            default -> base.scaled(1.1, 1.05);
        };
        s.cap(Cap.LONG);
        if (preset != Preset.MANE) mane(s, preset == Preset.SPIKY ? 17 : 14, 1.1, -70);
        return s;
    }

    private static HairSculpt beast(Preset preset, HairSculpt base) {
        if (preset == Preset.TEEN || preset == Preset.GOHAN_ADULT) {
            HairSculpt s = new HairSculpt().cap(Cap.FULL);
            s.spike(-.4, -8.6, -2.6, 80, 8, 16, 4.3, -14);                       // the towering crest
            s.pair(1.8, -8.6, -1.4, 77, 18, 13, 3.6, -14);
            s.pair(3.3, -8.3, .2, 62, 45, 10, 3.1, -12);
            s.spike(0, -8.4, 1.8, 72, 0, 12, 3.6, -16);
            s.pair(2.6, -7, 3.8, 45, 25, 8, 3, -10);
            s.spike(0, -5.8, 4.4, 30, 0, 6, 2.8, -6);
            s.pair(4.35, -6.5, -2.4, -60, 96, 2.6, 1.8, 0);
            return bang(s, -1.4);
        }
        return ssj2(preset, base).towering(1.38);
    }

    // ------------------------------------------------------------------ the characters

    /**
     * The Super Saiyan fan, after Goku's: wide locks standing up off the whole crown in three rows, tallest in the
     * middle, opening sideways towards wings at the temples, leaning further back row by row, with the hairline
     * lifted and the nape swept up. {@code spread} opens or closes the fan (Future Trunks stands nearly straight up);
     * {@code sharp} is the second grade: taller, thinner, sharper, with more locks.
     */
    static HairSculpt superFan(HairSculpt s, double len, double w, double spread, boolean sharp) {
        return superFan(s, len, w, spread, sharp, 0);
    }

    /** The same leaning further back by {@code back} degrees (slicked and swept cuts). */
    static HairSculpt superFan(HairSculpt s, double len, double w, double spread, boolean sharp, double back) {
        s.cap(Cap.FULL);
        double tp = sharp ? .8 : .68, sp = spread * .82;
        s.fan(-2.2, 0, 8 + back, 12 * len, 5 * w, -3, tp);                              // the front row
        s.fan(-2.2, 22 * sp, 9 + back, 11.2 * len, 4.8 * w, -6, tp);
        s.fan(-2.2, -20 * sp, 9 + back, 11.6 * len, 4.8 * w, -6, tp);
        s.fans(-2, 44 * sp, 10 + back, 10 * len, 4.5 * w, -8, tp);
        s.fans(-1.6, 66 * sp, 12 + back, 8.4 * len, 4.1 * w, -8, tp);
        s.fans(-1, Math.min(78, 80 * sp + 8), 14, 6.2 * len, 3.6 * w, -6, tp);    // the wings
        s.fans(.8, 10 * sp, 22 + back, 11.4 * len, 4.8 * w, -8, tp);                    // the middle row
        s.fans(.8, 34 * sp, 22 + back, 10.6 * len, 4.6 * w, -8, tp);
        s.fans(.8, 58 * sp, 22 + back, 9.2 * len, 4.3 * w, -10, tp);
        s.fan(3, 0, 40 + back, 10 * len, 4.8 * w, -8, tp);                               // the back row
        s.fans(3, 26 * sp, 40 + back, 9.4 * len, 4.5 * w, -8, tp);
        s.fans(3, 52 * sp, 40 + back, 8.4 * len, 4.2 * w, -10, tp);
        s.pair(2.2, -5.6, 4.35, 34, 20, 6.2 * len, 4 * w, -4);                   // the nape, up and back
        s.spike(0, -4.8, 4.4, 26, 0, 5.6 * len, 4 * w, -4);
        s.spike(-.6, -8.4, -3.7, 76, 172, 5.4 * len, 3.4 * w, 10);               // the hairline, lifted
        s.pair(2.5, -8.3, -3.5, 70, 150, 5 * len, 3 * w, 8);
        s.pair(4.35, -6.4, -2.4, -62, 96, 2.8, 2, 0);                             // sideburns
        if (sharp) {
            s.fans(-.6, 32 * sp, 14 + back, 12.6 * len, 3.4 * w, -5, .86);
            s.fans(2, 70 * sp, 30 + back, 8.6 * len, 3.2 * w, -8, .86);
        }
        return s;
    }

    /** The single loose lock falling over the brow (Super Saiyan 2). */
    static HairSculpt bang(HairSculpt s, double x) {
        return s.spike(x, -8.4, -3.9, -40, 182 + x * 4, 5.6, 1.8, -32, 0, .22);
    }

    /** Broly's Legendary hair: a towering mass of spikes thrown up and out, no bangs. */
    static HairSculpt legendary(HairSculpt s, double sc) {
        superFan(s, sc, 1.15, 1.1, true);
        s.pair(4.4, -6, 1.5, 22, 96, 7.5 * sc, 3, -2);
        s.pair(3.8, -5.2, 3.6, 18, 50, 7 * sc, 3, -2);
        return s;
    }

    /** Raditz turned Super Saiyan: the crown lifted, the long hair stiff and fanned out behind. */
    static HairSculpt raditzSsj(HairSculpt s, double sc) {
        s.cap(Cap.LONG);
        s.fan(-2.2, 0, 14, 8.6 * sc, 5, -4, .66);
        s.fans(-2, 26, 16, 8 * sc, 4.6, -8, .66);
        s.fans(-1.6, 52, 18, 7.4 * sc, 4.2, -10, .66);
        s.fans(-1, 80, 20, 6 * sc, 3.6, -8, .66);
        s.fans(.8, 18, 34, 8 * sc, 4.6, -10, .66);
        s.spike(-.6, -8.4, -3.7, 74, 172, 4.6, 3.4, 14);
        s.pair(2.5, -8.3, -3.5, 66, 146, 4.2, 3, 10);
        mane(s, 26 * sc, 1.1, -62);
        return s;
    }

    /** Future Trunks: straight hair parted in the middle, bangs to the eyes, the sides to the jaw. */
    static HairSculpt trunks(HairSculpt s) {
        s.cap(Cap.FULL);
        s.pair(.9, -8.75, -2.2, 15, 120, 4.5, 3, -95, 0, .15);                  // from the parting over and down
        s.pair(1.1, -8.75, .4, 15, 95, 4.8, 3.2, -100, 0, .15);
        s.pair(1, -8.75, 2.6, 15, 60, 5, 3.2, -100, 0, .15);
        s.pair(.9, -8.5, -3.7, -62, 168, 5.4, 2.6, -12, 0, .15);                // bangs
        s.pair(2.7, -8.3, -3.5, -70, 145, 5.6, 2.5, -8, 0, .15);
        s.pair(4.35, -8, -1.6, -85, 92, 6.6, 2.6, 0, 0, .15);                   // sides
        s.pair(4.35, -8, .8, -85, 88, 6.8, 2.6, 0, 0, .15);
        s.pair(4.3, -7.8, 3, -82, 70, 6.5, 2.6, 0, 0, .15);
        for (int i = -2; i <= 2; i++) s.spike(i * 1.75, -7.8, 4.35, -84, i * 6, 7, 2.5, 0, 0, .15);   // the back
        return s;
    }

    /** Vegeta's Super Saiyan 4: the flame gone, his hair grown long and shaggy with bangs on the brow. */
    static HairSculpt vegetaSsj4(HairSculpt s) {
        s.spike(0, -8.6, -2, 60, 0, 7, 3.6, -40);
        s.pair(2.3, -8.5, -1.5, 55, 30, 7, 3.3, -45);
        s.pair(3.8, -8, 0, 40, 70, 6.5, 3, -50);
        s.spike(0, -8.4, -3.8, -50, 180, 4.5, 2.4, -20, 0, .2);
        s.pair(1.9, -8.4, -3.6, -55, 160, 4.2, 2.2, -20, 0, .2);
        s.pair(4.35, -6.5, -2.4, -60, 96, 3, 1.9, 0);
        return s;
    }

    /** Super Saiyan 3's crest: the hair rising off the brow and sweeping back over the crown into the mane. */
    static HairSculpt crest(HairSculpt s) {
        s.fan(-2.6, 0, 42, 10.5, 4.8, -40, .7);
        s.fans(-2.4, 24, 44, 9.6, 4.4, -42, .7);
        s.fans(-2, 48, 46, 8.6, 4, -45, .7);
        s.fans(-1.2, 72, 40, 6.6, 3.6, -40, .7);
        s.fans(.8, 14, 66, 8.4, 4.4, -40, .7);
        s.pair(4.35, -6.5, -2.4, -60, 96, 2.6, 1.8, 0);
        return s;
    }

    /**
     * A mane down the back: three rows of locks leaving the crown and back of the head, arching back and then falling
     * (to {@code fall} degrees: -88 straight down, -50 fanned out), the middle ones longest, the tips staggered.
     */
    static void mane(HairSculpt s, double length, double spread, double fall) {
        for (int row = 0; row < 3; row++) {
            for (int i = -3; i <= 3; i++) {
                double x = i * 1.25 * Math.min(spread, 1.1);
                double y = -8.3 + row * 1.9, z = Math.min(4.4, 1.2 + row * 1.5);
                double up = 6 - row * 12;
                double len = length * (1 - .14 * Math.abs(i) / 3.0) - row * 1.5 + ((i + row) & 1) * 2.5;
                s.spike(x, y, z, up, i * 6 * spread, len, 3.8 - row * .3, fall - up, 0, .3, .25, .55);
            }
        }
        s.pair(4.4, -7, 1.2, -10, 60, length * .4, 3.2, fall + 30, -20, .3, .3, .6);   // either side, behind the ears
    }

    // ------------------------------------------------------------------ the other cuts
    /** The other cuts turned Super Saiyan: a fan cut to their shape, their long parts stiffened and fanned out. */
    static HairSculpt genericSsj(Preset preset, HairSculpt base, boolean sharp) {
        double g = sharp ? 1.14 : 1;
        HairSculpt s = new HairSculpt();
        switch (preset) {
            case MOHAWK -> {
                s.cap(Cap.MOHAWK);
                for (int i = 0; i < 5; i++) s.spike(0, -8.7, -3.4 + i * 1.7, 84 - i * 9, 0, (10.5 - i * .9) * g, 3.2, -6, 0, .1, 1, sharp ? .82 : .7);
                return s;
            }
            case BUZZ -> superFan(s, .5 * g, .95, .95, sharp);
            case SLICK, PONYTAIL, TOPKNOT -> superFan(s, .88 * g, .95, .8, sharp, 26);
            case SWEPT -> superFan(s, .92 * g, .95, .85, sharp, 18);
            case SIDECUT -> superFan(s, .85 * g, .9, .75, sharp, 14);
            case PUFF -> superFan(s, .78 * g, 1.15, 1.1, sharp);
            case BOWL -> superFan(s, .82 * g, 1, .9, sharp);
            default -> superFan(s, .92 * g, 1, .9, sharp);
        }
        if (preset == Preset.TOPKNOT) s.spike(0, -9, 2, 70, 0, 4, 3.4, -10);
        stiffLongs(s, base);
        return sharp ? bang(s, -1.2) : s;
    }

    /** The long locks of a cut (tails, drapes, manes), stiffened and fanned outward. */
    static void stiffLongs(HairSculpt s, HairSculpt base) {
        for (HairSculpt.Lock l : base.locks) {
            double[] k = l.k();
            int last = (l.knots() - 1) * 5;
            double ox = k[last] - k[0], oy = k[last + 1] - k[1], oz = k[last + 2] - k[2];
            if (oy < 6) continue;
            double len = 0;
            for (int i = 1; i < l.knots(); i++) {
                int o = i * 5, p = o - 5;
                len += Math.sqrt(Math.pow(k[o] - k[p], 2) + Math.pow(k[o + 1] - k[p + 1], 2) + Math.pow(k[o + 2] - k[p + 2], 2));
            }
            double az = Math.toDegrees(Math.atan2(ox + k[0] * .6, Math.max(.5, oz + k[2] * .3)));
            s.spike(k[0], k[1], k[2], -28, az, len * .95, Math.max(2.6, k[3] * 1.1), -38, 0, .25, .7, .62);
        }
    }
}
