package com.dbzenith.client.hair;

import com.dbzenith.appearance.HairCode.Preset;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every stock hairstyle in every look, sculpted after the show's designs (the Daizenshuu Super Saiyan sheet and the
 * series' own art):
 * <ul>
 *   <li>Super Saiyan Goku: a tall flame of about nine tapering spikes, mostly upright, the outer ones curving out,
 *       wings over the ears, the hairline lifted, two thin bangs; Super Saiyan 2 taller and sharper with one bang;
 *       Super Saiyan 3 a crest swept back into a mane to the waist; Super Saiyan 4 his own hair grown long and wild;
 *       God his own hair, slimmer.</li>
 *   <li>Vegeta: a dense upright flame from a deep widow's peak, the temples bare; taller and sharper as a Super Saiyan,
 *       rigid even in the third grade; shaggy with bangs as a Super Saiyan 4.</li>
 *   <li>Gohan: as a boy short spikes and bangs, the second grade very tall and upright with his one bang; as a man
 *       short spikes swept up and back with a forehead lock; Beast one towering crest.</li>
 *   <li>Broly: wild hair to the shoulders, bangs over the eyes; Legendary a huge mass thrown up and back.</li>
 *   <li>Raditz: a mane past the knees under a spiky crown. Future Trunks: straight, parted, to the neck; as a Super
 *       Saiyan spiky and lifted with the bangs still hanging.</li>
 * </ul>
 * The other cuts are made well and turn Super Saiyan in their own way (a swept cut sweeps back, tails stiffen and
 * flare, a mohawk becomes a crest).
 */
public final class HairStyles {
    private static final Map<Preset, EnumMap<HairLook, HairShape>> SHAPES = new EnumMap<>(Preset.class);
    private static final Map<String, HairShape> MORPHS = new LinkedHashMap<>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, HairShape> e) {
            return size() > 160;
        }
    };

    private HairStyles() {}

    /** A stock hairstyle in a look. */
    public static synchronized HairShape of(Preset preset, HairLook look) {
        return SHAPES.computeIfAbsent(preset, p -> new EnumMap<>(HairLook.class)).computeIfAbsent(look, l -> author(preset, l));
    }

    /** Part of the way from one look to another (64 steps, each built once). */
    public static synchronized HairShape between(Preset preset, HairLook from, HairLook to, float progress) {
        if (from == to || progress <= 0) return of(preset, from);
        if (progress >= 1) return of(preset, to);
        int step = Math.max(1, Math.min(63, Math.round(progress * 64)));
        return MORPHS.computeIfAbsent(preset + "|" + from + ">" + to + "|" + step,
                k -> HairShape.morph(of(preset, from), of(preset, to), step / 64f));
    }

    private static HairShape author(Preset preset, HairLook look) {
        if (preset == Preset.BALD) return look == HairLook.BASE ? HairShape.BALD : author(Preset.SPIKY, look);
        HairShape base = base(preset);
        return switch (look) {
            case BASE -> base;
            case GOD -> Sculpt.slimmed(base, .76, .92);
            case SSJ -> ssj(preset, base, false);
            case SSJ2 -> ssj(preset, base, true);
            case SSJ3 -> ssj3(preset, base);
            case SSJ4 -> ssj4(preset, base);
            case BEAST -> beast(preset, base);
        };
    }

    // ================================================================== the own hair

    static HairShape base(Preset preset) {
        Sculpt s = new Sculpt();
        switch (preset) {
            case SPIKY -> goku(s);
            case PRINCE -> vegeta(s, 1, 0, 1.2);
            case TEEN -> gohanBoy(s);
            case GOHAN_ADULT -> gohanMan(s);
            case WILD -> broly(s);
            case MANE -> raditz(s);
            case CURTAINS -> trunks(s);
            case SAGE -> parted(s, 15, true);
            case LONG -> parted(s, 24, false);
            case SWEPT -> swept(s);
            case SIDECUT -> sidecut(s);
            case SLICK -> slick(s);
            case BOWL -> bowl(s);
            case BUZZ -> s.cap(-7.3, 0, .4, -5.2, -3.6);
            case PUFF -> puff(s);
            case MOHAWK -> mohawk(s, 1, false);
            case PONYTAIL -> { slick(s); ponytail(s, false); }
            case TWINTAILS -> { parted(s, 0, true); twintails(s, false); }
            case TOPKNOT -> { slick(s); topknot(s); }
            default -> {}
        }
        return s.build();
    }

    /** Goku's own hair: the hand-placed layout (a big fan over his right eye, a crest, a lower wing, a rear fan, bangs). */
    static void goku(Sculpt s) {
        s.cap(-7.5, 0, .3, -5.4, -4.2).feel(.6f, 1);
        s.knots(-.65, -8.8, -1.7, 4.9, 3.5, -1.7, -11.3, -1.45, 4.8, 3.4, -3.85, -13.8, -.95, 3.9, 2.9, -6.15, -15.35, -.4, 2.85, 2.3, -8.2, -16.15, .15, 1.8, 1.7);
        s.knots(-2.4, -8.5, -.9, 4.1, 3.2, -4.15, -10.1, -.65, 4.05, 3.1, -6.55, -11.35, -.25, 3.3, 2.6, -8.25, -11.95, .1, 2.65, 2.15, -10.2, -12.4, .4, 1.75, 1.7);
        s.knots(1.15, -8.8, .15, 2.8, 2.6, 1.55, -11.4, .3, 2.55, 2.45, 1.4, -13.9, .65, 2.05, 2.05, .8, -16.2, .95, 1.4, 1.55, .2, -17.65, 1.2, .85, 1.05);
        s.knots(2, -8.6, .5, 3.9, 3, 4.2, -9.35, .9, 3.75, 2.85, 6.2, -9.85, 1.3, 3, 2.45, 8, -10.3, 1.65, 2.15, 1.85, 9, -10.8, 1.9, 1.25, 1.3);
        s.knots(-.8, -8.8, 2.65, 4.1, 3.5, -1.8, -10.9, 3.35, 3.85, 3.15, -3.55, -12.6, 4.35, 2.9, 2.65, -5.4, -13.65, 5.5, 1.9, 1.85);
        s.straight(1.65, -8.35, 3.1, 7.1, -10.55, 6.8, 3.55, 2.8);
        s.straight(-2.5, -7.6, 3.1, -8.5, -8.55, 5.6, 3.1, 2.5);
        s.knots(-.35, -9, -3.65, 3.45, 2.2, -.8, -7.85, -4.65, 3.15, 1.8, -1.85, -6.75, -5.05, 2.4, 1.35, -2.75, -5.6, -5.1, 1.35, .95);
        s.knots(2.5, -8.8, -3.8, 2.65, 1.95, 2.75, -7.55, -4.75, 2.3, 1.6, 2.5, -6.25, -5.1, 1.55, 1.1, 2, -5.05, -5.1, .8, .75);
        s.knots(-2.7, -8.6, -3.6, 2.1, 1.65, -3.55, -7.15, -4.3, 1.65, 1.25, -3.9, -5.6, -4.55, .95, .8);
        for (int side : new int[]{-1, 1}) {                                         // staggered side fans
            for (int tier = 0; tier < 3; tier++) {
                double y = -7.2 + tier * 1.25;
                s.straight(side * 3.9, y, .5 + tier * .95, side * (4.8 + 1.15 * (2.5 - tier * .4)), y - .1, 3 + tier * .95, 2.05, 1.85);
            }
            s.straight(side * 3.9, -7.7, -2.65, side * 4.75, -4.8, -2.4, 1.65, 1.45);
        }
        for (int i = -2; i <= 2; i++) {                                              // nape shingles
            double x = i * 1.8;
            s.straight(x, -5.3, 4.4, x + i * .35, -3.05 + (Math.abs(i) % 2) * .45, 6.25, 2.2, 1.5);
        }
    }

    /**
     * Vegeta's flame: spikes off the whole crown standing almost straight up and drawing together into one tall
     * flame, leaning a little back, from a deep widow's peak with the temples bare; short spikes over the ears.
     */
    static void vegeta(Sculpt s, double len, double back, double width) {
        s.cap(-7, 1.7, 1.5, -6.6, -4.6).feel(.75f, .8f);
        s.jagged = .2;
        s.lock(0, -8.3, -3.4, 82, 0, 13.5 * len, 4.4 * width, -4 - back);          // the front spike, off the peak
        s.pair(1.6, -8.5, -2.6, 80, 10, 12.6 * len, 4 * width, -5 - back);
        s.pair(3, -8.3, -1.8, 74, 28, 11 * len, 3.7 * width, -6 - back);
        s.lock(0, -8.5, -.6, 78, 0, 13 * len, 4.4 * width, -8 - back);
        s.pair(1.9, -8.5, -.2, 76, 14, 12.2 * len, 4 * width, -8 - back);
        s.pair(3.5, -8.2, .4, 70, 30, 10.4 * len, 3.6 * width, -9 - back);
        s.lock(0, -8.4, 2, 68, 0, 11.6 * len, 4.4 * width, -10 - back);
        s.pair(2.2, -8.2, 2.4, 66, 16, 10.8 * len, 4 * width, -10 - back);
        s.pair(3.6, -7.8, 2.6, 58, 32, 9 * len, 3.5 * width, -10 - back);
        s.lock(0, -7.2, 3.9, 50, 0, 9 * len, 4 * width, -10 - back);
        s.pair(2.4, -7, 3.9, 46, 18, 8 * len, 3.6 * width, -10 - back);
        s.pair(4.2, -7, -.6, 48, 70, 5 * len, 2.6 * width, -6);                    // over the ears
        s.pair(4.2, -6.6, 1.4, 40, 55, 5.4 * len, 2.6 * width, -6);
        s.pair(1.6, -5.4, 4.3, -10, 10, 3.2, 2.4 * width, -20);                     // the nape
    }

    /** Gohan as a boy: short spikes off the crown leaning back and out, three bangs over the brow. */
    static void gohanBoy(Sculpt s) {
        s.cap(-7.2, 0, .3, -5.4, -4).feel(.6f, 1);
        s.fan(-2, 0, 30, 7, 4.4, -18);
        s.fans(-1.8, 30, 32, 6.6, 4.1, -20);
        s.fans(-1.2, 60, 30, 6, 3.8, -20);
        s.fans(1, 14, 50, 6.6, 4.2, -18);
        s.fans(1, 44, 48, 6, 3.9, -20);
        s.fan(3, 0, 64, 6, 4.2, -16);
        s.fans(3, 30, 64, 5.4, 3.9, -18);
        s.pair(4.3, -6.6, -.6, 10, 80, 4.4, 2.8, -24);
        s.pair(2, -5.4, 4.3, -20, 15, 3.6, 3, -16);
        s.lock(-.8, -8.4, -3.8, -50, 176, 4.2, 2.8, -20);                         // bangs
        s.lock(1.2, -8.4, -3.8, -55, 186, 3.8, 2.6, -18);
        s.lock(-2.6, -8.2, -3.6, -48, 160, 3.4, 2.4, -16);
        s.pair(4.35, -6.4, -2.4, -62, 96, 2.4, 1.8, 0);
    }

    /** Gohan as a man: short spikes swept up and back, one lock fallen over his forehead. */
    static void gohanMan(Sculpt s) {
        s.cap(-7.4, 0, .5, -5.6, -4.2).feel(.65f, 1);
        s.fan(-2.6, 0, 40, 6.8, 4.4, -10);
        s.fans(-2.4, 26, 42, 6.4, 4.1, -12);
        s.fans(-2, 52, 40, 5.8, 3.8, -14);
        s.fans(.6, 14, 56, 6.4, 4.2, -14);
        s.fans(.6, 40, 54, 5.8, 3.9, -16);
        s.fan(2.8, 0, 68, 5.6, 4.2, -14);
        s.fans(2.8, 30, 66, 5, 3.9, -14);
        s.pair(4.3, -6.6, -.4, 20, 70, 4, 2.6, -16);
        s.pair(2, -5.4, 4.3, -10, 12, 3.2, 3, -16);
        s.lock(-1, -8.4, -3.8, -30, 182, 4.4, 2.4, -40);                          // the forehead lock
        s.pair(4.35, -6.4, -2.4, -62, 96, 2.2, 1.8, 0);
    }

    /** Broly: wild hair falling to the shoulders, spiked at the crown, bangs hanging over the eyes. */
    static void broly(Sculpt s) {
        s.cap(-6.8, 0, 0, -3.6, -1.2).feel(.45f, 1);
        s.fan(-1.8, 0, 40, 7, 4.6, -30);
        s.fans(-1.5, 30, 40, 6.6, 4.3, -34);
        s.fans(.8, 16, 60, 7, 4.4, -40);
        s.fans(.8, 48, 56, 6.6, 4.1, -44);
        s.feel(.3f, 1);
        for (int i = -3; i <= 3; i++) s.lock(i * 1.3, -7.6, 3.8, -20, i * 9, 12 - Math.abs(i) * .6 + (i & 1) * 1.5, 3.6, -48, 0, .45, .6);
        s.pair(4.3, -7.4, .8, -30, 88, 10, 3.4, -40, 10, .45, .6);
        s.pair(4.3, -7.6, -1.6, -50, 100, 8.4, 3, -26, 0, .45, .7);
        s.feel(.4f, 1);
        s.lock(-.4, -8.4, -3.8, -58, 180, 6, 3, -14);                             // bangs over the eyes
        s.lock(1.6, -8.4, -3.7, -62, 190, 5.4, 2.8, -12);
        s.lock(-2.4, -8.2, -3.6, -60, 168, 5.6, 2.8, -12);
        s.pair(3.6, -8, -3.2, -66, 130, 5, 2.6, -10);
    }

    /** Raditz: a spiky crown and a mane falling past his knees, fanning out a little as it falls. */
    static void raditz(Sculpt s) {
        s.cap(-7.2, .8, .6, -4.6, -2).feel(.6f, 1);
        s.fan(-2.4, 0, 30, 6.6, 4.4, -14);
        s.fans(-2.2, 28, 32, 6.2, 4.1, -16);
        s.fans(-1.6, 56, 30, 5.6, 3.8, -16);
        s.fans(.6, 16, 46, 6, 4.2, -18);
        s.lock(0, -8.4, -3.7, -60, 180, 3.6, 2.6, -14);
        manes(s, 38, 1.1, -88, .12f);
    }

    /** Future Trunks: straight hair parted in the middle, bangs to the eyes, the sides over the ears to the jaw. */
    static void trunks(Sculpt s) {
        s.cap(-6.2, 0, 0, -3.4, -1.4).feel(.4f, 1);
        s.pair(.7, -8.75, -2.4, 12, 115, 4.8, 3.2, -100, 0, .4, .6);               // over from the parting and down
        s.pair(.9, -8.75, .2, 12, 92, 5.2, 3.4, -104, 0, .4, .6);
        s.pair(.8, -8.75, 2.6, 12, 58, 5.4, 3.4, -104, 0, .4, .6);
        s.pair(.8, -8.5, -3.8, -64, 170, 5.6, 2.8, -10, 0, .4, 1);                // bangs
        s.pair(2.6, -8.3, -3.6, -72, 148, 6, 2.7, -6, 0, .4, 1);
        s.pair(4.4, -7.9, -1.6, -86, 94, 7.2, 2.8, 0, 0, .4, 1);                  // the sides
        s.pair(4.4, -7.9, .9, -86, 88, 7.4, 2.8, 0, 0, .4, 1);
        s.pair(4.3, -7.6, 3, -84, 66, 7, 2.8, 0, 0, .4, 1);
        for (int i = -2; i <= 2; i++) s.lock(i * 1.75, -7.6, 4.4, -86, i * 6, 7.6, 2.7, 0, 0, .4, 1);
    }

    /** Long hair parted in the middle, falling straight down the back ({@code length}), bangs swept aside. */
    static void parted(Sculpt s, double length, boolean bangs) {
        s.cap(-6.6, 0, .3, -3.2, -1).feel(.35f, 1);
        s.pair(.7, -8.75, -2.4, 12, 115, 4.6, 3.2, -100, 0, .4, .6);
        s.pair(.9, -8.75, .2, 12, 92, 5, 3.4, -104, 0, .4, .6);
        s.pair(.8, -8.75, 2.6, 12, 58, 5.2, 3.4, -104, 0, .4, .6);
        if (bangs) {
            s.pair(.8, -8.5, -3.8, -50, 150, 4.8, 2.8, -24, 0, .4, 1);
            s.pair(2.6, -8.3, -3.6, -66, 130, 5.4, 2.6, -14, 0, .4, 1);
        }
        s.pair(4.4, -7.9, -1.6, -86, 94, 8, 2.8, 0, 0, .4, 1);
        if (length > 0) {
            s.feel(.2f, 1);
            s.pair(4.4, -7.9, .9, -86, 88, length * .8, 4, 0, 0, .5, 1);
            for (int i = -2; i <= 2; i++) s.lock(i * 1.75, -7.6, 4.4, -84, i * 5, length - Math.abs(i) * .8, 4.2, 0, 0, .5, 1);
        } else {
            s.pair(4.4, -7.9, .9, -86, 88, 7, 3, 0, 0, .4, 1);
            for (int i = -2; i <= 2; i++) s.lock(i * 1.75, -7.6, 4.4, -84, i * 5, 7, 3, 0, 0, .4, 1);
        }
    }

    /** Swept back off the face, a little volume on top, ending at the collar. */
    static void swept(Sculpt s) {
        s.cap(-7.4, 0, .6, -5, -2.6).feel(.5f, 1);
        for (int i = -2; i <= 2; i++) s.lock(i * 1.7, -8.4, -3.7, 30, i * 4, 8.6, 3.2, -46, 0, .45, 1);
        for (int i = -2; i <= 2; i++) s.lock(i * 1.8, -8.6, -.5, 10, i * 6, 7, 3.2, -50, 0, .45, 1);
        s.pair(4.3, -7.4, -1.6, 0, 20, 6.4, 3, -60, 0, .45, 1);
        s.pair(4.3, -7.2, 1.2, -10, 16, 5.4, 3, -60, 0, .45, 1);
    }

    /** Shaved at the sides, the top grown long and swept over to the left. */
    static void sidecut(Sculpt s) {
        s.cap(-7.5, 0, .4, -7.6, -5.4).feel(.5f, 1);
        for (int i = 0; i < 4; i++) s.lock(-2.4, -8.6, -3 + i * 2, 30, 80, 7.6 - i * .4, 3.4, -50, 0, .45, 1);
        for (int i = 0; i < 4; i++) s.lock(.2, -8.6, -2.6 + i * 2, 20, 84, 6.6 - i * .3, 3.2, -54, 0, .45, 1);
    }

    /** Slicked flat back over the crown. */
    static void slick(Sculpt s) {
        s.cap(-7.6, 0, .6, -5.4, -3.4).feel(.7f, .6f);
        for (int i = -2; i <= 2; i++) s.lock(i * 1.8, -8.5, -3.8, 4, i * 3, 8.4, 3, -30, 0, .35, 1);
        s.pair(4.3, -7.6, -1.6, -6, 6, 5, 2.6, -40, 0, .35, 1);
    }

    /** A bowl cut: a straight fringe on the brow, the sides level with it all round. */
    static void bowl(Sculpt s) {
        s.cap(-5.6, 0, 0, -5.2, -4).feel(.55f, 1);
        for (int i = -2; i <= 2; i++) s.lock(i * 1.8, -8.6, -2.4, 20, 180 - i * 8, 3.6, 3.4, -100, 0, .4, .6);
        for (int s1 : new int[]{-1, 1}) for (int i = 0; i < 3; i++) s.lock(s1 * 3, -8.6, -1 + i * 2, 20, s1 * 90, 3.2, 3.4, -100, 0, .4, .6);
        for (int i = -2; i <= 2; i++) s.lock(i * 1.8, -8.6, 2.6, 20, i * 6, 3.6, 3.4, -100, 0, .4, .6);
    }

    /** A round puff of short curls standing out all over the head. */
    static void puff(Sculpt s) {
        s.cap(-7, 0, 0, -5, -3.2).feel(.7f, .6f);
        s.jagged = .12;
        double[][] rings = {{90, 1}, {64, 7}, {38, 11}, {12, 12}};
        for (double[] ring : rings) {
            for (int i = 0; i < ring[1]; i++) {
                double a = i * 360.0 / ring[1] + ring[0] * .37;
                double[] d = Sculpt.dir(ring[0], a);
                double x = d[0] * 4.1, y = -4.6 + d[1] * 4.3, z = d[2] * 4.1 + .3;
                s.lock(x, Math.max(-8.6, y), Math.max(-3.6, z), ring[0] + 6, a, 4, 5, -24, 0, .85, 1);
            }
        }
    }

    /** A strip of upright hair over the crown, the sides shaved. */
    static void mohawk(Sculpt s, double len, boolean sharp) {
        s.cap(-7.6, 0, 1.8, -7.9, -7.9).feel(.8f, .5f);
        s.jagged = .1;
        for (int i = 0; i < 6; i++) s.lock(0, -8.6, -3.2 + i * 1.4, 84 - i * 9, 0, (6.6 - i * .4) * len, sharp ? 3 : 3.4, -8, 0, .45, 1);
    }

    /** A high ponytail tied at the back of the crown: hanging, or (Super Saiyan) stiff and flared out in spikes. */
    static void ponytail(Sculpt s, boolean stiff) {
        s.feel(stiff ? .75f : .16f, 1);
        s.lock(0, -8.6, 3.2, 40, 0, 2.4, 4.6, -20, 0, .8, 1);                       // the tie
        if (stiff) for (int i = -1; i <= 1; i++) s.lock(i * 1.3, -8.8, 4.4, 20 - Math.abs(i) * 14, i * 26, 11.5 - Math.abs(i) * 1.5, 4.6, -46, 0, .55, 1);
        else for (int i = -1; i <= 1; i++) s.lock(i * 1.2, -8.8, 4.4, 10, i * 8, 14, 4.4, -100, 0, .55, .5);
    }

    /** Two tails tied low at the sides of the head: hanging, or (Super Saiyan) stiff and flared out. */
    static void twintails(Sculpt s, boolean stiff) {
        s.feel(stiff ? .75f : .18f, 1);
        for (int side : new int[]{-1, 1}) {
            s.lock(side * 4.4, -6.4, 1.4, 0, side * 90, 1.8, 4, 0, 0, .8, 1);
            for (int i = 0; i < 2; i++) {
                if (stiff) s.lock(side * 5.6, -6.6 + i, 1.4 + i * .6, -16 - i * 14, side * (62 + i * 20), 10.5, 4.2, -36, 0, .55, 1);
                else s.lock(side * 5.6, -6.6 + i, 1.4 + i * .6, -40, side * 80, 12, 4, -50, 0, .55, .5);
            }
        }
    }

    /** A bun on top of the head. */
    static void topknot(Sculpt s) {
        s.feel(.9f, .3f);
        for (int i = 0; i < 6; i++) s.lock(Math.sin(i) * .6, -8.8, 1 + Math.cos(i) * .6, 60, i * 60, 3, 3.4, -200, 0, .9, 1);
    }

    // ================================================================== Super Saiyan

    /**
     * Super Saiyan (and the second grade with {@code two}): each character the way the designs draw them; the other
     * cuts with the Super Saiyan flame cut to their shape.
     */
    private static HairShape ssj(Preset preset, HairShape base, boolean two) {
        Sculpt s = new Sculpt();
        double g = two ? 1.14 : 1;
        switch (preset) {
            case SPIKY -> { flame(s, g, two ? .86 : 1, two ? .85 : 1, 0, two); if (!two) twoBangs(s); }
            case TEEN -> { if (two) flame(s, 1.14, .82, .62, -4, true); else { flame(s, .9, .95, .95, 4, false); twoBangs(s); } }
            case GOHAN_ADULT -> { flame(s, two ? .9 : .76, .92, .8, two ? 26 : 34, two); if (!two) bang(s, -1.1); }
            case PRINCE -> { return vegetaSuper(s, two ? 1.28 : 1.14, two); }
            case WILD -> { flame(s, two ? 1.42 : 1.26, 1.2, 1.05, 16, two); s.pair(4.4, -6, 1.5, 24, 96, 8 * g, 3.4, -4); s.pair(3.8, -5.2, 3.6, 18, 50, 8 * g, 3.4, -4); }
            case MANE -> { flame(s, .95 * g, 1, .9, 14, two); s.cap(-7.2, .8, .6, -4.6, -2); manes(s, 34 * g, 1.4, -62, .3f); }
            case CURTAINS -> { flame(s, 1.04 * g, .95, .66, 6, two); trunksBangs(s); }
            case SWEPT -> flame(s, .92 * g, .95, .85, 30, two);
            case SIDECUT -> { flame(s, .85 * g, .9, .75, 16, two); s.cap(-7.5, 0, .4, -7.6, -5.4); }
            case SLICK -> flame(s, .86 * g, .95, .8, 34, two);
            case BOWL -> flame(s, .82 * g, 1, .9, 4, two);
            case BUZZ -> flame(s, .5 * g, .95, .95, 6, two);
            case PUFF -> flame(s, .74 * g, 1.2, 1.15, 4, two);
            case MOHAWK -> { mohawk(s, 1.7 * g, true); return s.build(); }
            case PONYTAIL -> { flame(s, .84 * g, .95, .8, 24, two); ponytail(s, true); }
            case TWINTAILS -> { flame(s, .84 * g, .95, .85, 10, two); twintails(s, true); }
            case TOPKNOT -> { flame(s, .84 * g, .95, .8, 24, two); topknot(s); }
            case SAGE, LONG -> { flame(s, .9 * g, .95, .85, 14, two); flaredLong(s, preset == Preset.LONG ? 22 : 15); }
            default -> flame(s, g, 1, 1, 0, two);
        }
        if (two && preset != Preset.TEEN && preset != Preset.WILD) bang(s, -1.2);
        if (two && preset == Preset.TEEN) bang(s, -1.3);
        return s.build();
    }

    /**
     * The Super Saiyan flame, after Goku's: tapering spikes standing off the whole crown in three rows, mostly upright
     * and drawing up into a flame, the outer ones curving out, wings over the ears, the nape swept up, the hairline
     * lifted. {@code len} and {@code w} scale it, {@code spread} opens or closes it, {@code lean} tips it back;
     * {@code sharp} is the second grade: thinner and sharper, more spikes.
     */
    static void flame(Sculpt s, double len, double w, double spread, double lean, boolean sharp) {
        s.cap(-7.4, 0, .4, -5.6, -4.2).feel(sharp ? .9f : .85f, .5f);
        double sp = spread;
        w *= 1.2;                                                                     // wide spikes: a full flame
        s.fan(-2.4, 0, 6 + lean, 12.4 * len, 4.8 * w, -2);                           // the front row
        s.fan(-2.4, 16 * sp, 7 + lean, 11.8 * len, 4.6 * w, -4);
        s.fan(-2.4, -15 * sp, 7 + lean, 12 * len, 4.6 * w, -4);
        s.fans(-2.2, 32 * sp, 8 + lean, 10.8 * len, 4.3 * w, -6);
        s.fans(-1.8, 50 * sp, 10 + lean, 9.2 * len, 4 * w, -8);
        s.fans(-1.2, Math.min(66, 56 * sp + 6), 14 + lean, 7.8 * len, 3.6 * w, -6);  // the outer flares
        s.fans(.6, 9 * sp, 20 + lean, 12 * len, 4.7 * w, -6);                        // the middle row
        s.fans(.6, 28 * sp, 20 + lean, 11.2 * len, 4.5 * w, -6);
        s.fans(.6, 42 * sp, 22 + lean, 9.8 * len, 4.2 * w, -8);
        s.fan(2.8, 0, 36 + lean, 10.6 * len, 4.7 * w, -6);                           // the back row
        s.fans(2.8, 22 * sp, 36 + lean, 10 * len, 4.4 * w, -6);
        s.fans(2.8, 38 * sp, 38 + lean, 9 * len, 4.1 * w, -8);
        s.pair(4.3, -6.9, -.2, 50, 72, 5.4 * len, 3 * w, 6);                         // wings over the ears
        s.pair(2.2, -5.6, 4.35, 34, 18, 6.6 * len, 4 * w, -2);                       // the nape, up and back
        s.lock(0, -4.8, 4.4, 26, 0, 6 * len, 4 * w, -2);
        s.lock(-.6, -8.4, -3.8, 74, 172, 5.4 * len, 3.4 * w, 10);                    // the hairline, lifted
        s.pair(2.5, -8.3, -3.6, 68, 150, 5 * len, 3 * w, 8);
        s.pair(4.35, -6.4, -2.4, -62, 96, 2.8, 2, 0);                                 // sideburns
        if (sharp) {
            s.fans(-1, 22 * sp, 12 + lean, 13 * len, 3.2 * w, -3);
            s.fans(1.8, 38 * sp, 28 + lean, 11.6 * len, 3.2 * w, -6);
        }
    }

    /** Super Saiyan Goku's two thin bangs over the brow. */
    static void twoBangs(Sculpt s) {
        s.lock(-1.4, -8.3, -3.9, -30, 186, 3.6, 1.7, -34);
        s.lock(.4, -8.3, -3.9, -40, 176, 3.2, 1.5, -30);
    }

    /** The single loose lock over the brow (Super Saiyan 2). */
    static void bang(Sculpt s, double x) {
        s.feel(.35f, 1).lock(x, -8.4, -3.9, -36, 182 + x * 4, 6, 1.9, -36);
    }

    /** Future Trunks' bangs, still hanging over his forehead when his hair stands up. */
    static void trunksBangs(Sculpt s) {
        s.feel(.5f, 1);
        s.lock(-.5, -8.4, -3.9, -55, 182, 5, 2.6, -14);
        s.lock(1.4, -8.4, -3.8, -60, 168, 4.6, 2.4, -12);
        s.lock(-2.3, -8.2, -3.7, -62, 196, 4.4, 2.4, -12);
    }

    /** Vegeta as a Super Saiyan: the flame taller and sharper, spikier at the edges, the widow's peak kept. */
    static HairShape vegetaSuper(Sculpt s, double len, boolean two) {
        vegeta(s, len, two ? 4 : 2, two ? 1.08 : 1.16);
        s.feel(.9f, .5f);
        s.jagged = .34;
        s.pair(3.8, -8, 1, 62, 40, 9 * len, 3 * (two ? .86 : .95), -10);
        s.pair(2.8, -8.4, 1.2, 74, 22, 11 * len, 3 * (two ? .86 : .95), -8);
        return s.build();
    }

    /** Long hair turned Super Saiyan: stiff and fanned out in spikes behind the shoulders. */
    static void flaredLong(Sculpt s, double length) {
        s.feel(.6f, .5f);
        for (int i = -3; i <= 3; i++) s.lock(i * 1.3, -7.4, 4.2, -20, i * 16, length - Math.abs(i) * .7 + (i & 1) * 2, 3.4, -30, 0, .45, 1);
        s.pair(4.4, -7.4, 1, -30, 80, length * .7, 3.2, -24);
    }

    // ================================================================== the third grade, the fourth, Beast

    /**
     * Super Saiyan 3: the crest swept back off the brow, the mane falling to the waist close down the back with spiky
     * tips, one long bang. Vegeta's stays a rigid flame, taller still.
     */
    private static HairShape ssj3(Preset preset, HairShape base) {
        Sculpt s = new Sculpt();
        if (preset == Preset.PRINCE) {
            vegeta(s, 1.42, 4, 1.08);
            s.feel(.9f, .5f);
            return s.build();
        }
        s.cap(-7.2, 0, .2, -4.6, -2).feel(.85f, .5f);
        s.jagged = .26;
        s.fan(-2.8, 0, 40, 10.6, 5, -40);                                           // the crest
        s.fans(-2.6, 22, 42, 10, 4.6, -42);
        s.fans(-2.2, 44, 44, 9, 4.2, -46);
        s.fans(-1.4, 68, 40, 7, 3.8, -40);
        s.fans(.6, 14, 64, 8.6, 4.4, -40);
        s.pair(4.35, -6.4, -2.4, -62, 96, 2.6, 1.8, 0);
        manes(s, preset == Preset.MANE ? 46 : 28, 1, -88, .16f);
        s.feel(.35f, 1).lock(-1.1, -8.5, -3.9, -64, 182, 6.4, 2.2, -16);           // the long bang
        return s.build();
    }

    /** Super Saiyan 4: the own hair grown bigger and wild, a mane falling down the back; Vegeta's shaggy, with bangs. */
    private static HairShape ssj4(Preset preset, HairShape base) {
        if (preset == Preset.PRINCE) {
            Sculpt s = new Sculpt().cap(-6.8, 0, 0, -4.4, -2).feel(.6f, 1);
            s.fan(-2.4, 0, 46, 7.4, 4.6, -40);
            s.fans(-2.2, 26, 46, 7, 4.3, -42);
            s.fans(-1.6, 52, 44, 6.6, 4, -46);
            s.fans(.6, 16, 60, 7.2, 4.4, -44);
            s.lock(-.2, -8.4, -3.9, -54, 180, 4.6, 2.8, -18);
            s.pair(1.8, -8.4, -3.7, -58, 160, 4.4, 2.6, -16);
            s.pair(4.35, -6.4, -2.4, -62, 96, 3, 1.9, 0);
            manes(s, 16, 1.1, -70, .3f);
            return s.build();
        }
        Sculpt s = new Sculpt().with(Sculpt.scaled(base, preset == Preset.SPIKY ? 1.16 : 1.1, 1.06, .55f, 1));
        s.cap(-7, 0, 0, -4.4, -2);
        if (preset != Preset.MANE) manes(s, preset == Preset.SPIKY ? 19 : 15, 1.1, -72, .3f);
        return s.build();
    }

    /** Beast: one towering crest drawn up off the brow (Gohan's), a bang hanging; other cuts the second grade drawn up. */
    private static HairShape beast(Preset preset, HairShape base) {
        if (preset == Preset.TEEN || preset == Preset.GOHAN_ADULT) {
            Sculpt s = new Sculpt().cap(-7.4, 0, .4, -5.6, -4.2).feel(.92f, .4f);
            s.jagged = .14;
            s.lock(-.4, -8.6, -2.8, 82, 8, 19, 5, -12);                              // the crest
            s.pair(1.7, -8.6, -1.8, 80, 14, 16.5, 4.4, -12);
            s.pair(3.2, -8.3, -.4, 70, 36, 12.5, 3.8, -12);
            s.lock(0, -8.5, .6, 76, 0, 16, 4.6, -16);
            s.pair(2, -8.3, 1.6, 70, 18, 13.5, 4.2, -16);
            s.lock(0, -7.6, 3.4, 56, 0, 11, 4.2, -16);
            s.pair(2.6, -6.8, 3.8, 46, 24, 8.4, 3.6, -12);
            s.pair(4.3, -6.8, -.4, 34, 72, 6, 3, -6);
            s.pair(4.35, -6.4, -2.4, -62, 96, 2.8, 1.9, 0);
            bang(s, -1.4);
            return s.build();
        }
        return Sculpt.towering(ssj(preset, base, true), 1.36);
    }

    /**
     * A mane down the back: three rows of locks leaving the crown and the back of the head, bending down early and
     * falling close behind (to {@code fall} degrees: -88 straight down, -60 fanned out), the middle ones longest, the
     * tips staggered.
     */
    static void manes(Sculpt s, double length, double spread, double fall, float stiff) {
        s.feel(stiff, 1);
        for (int row = 0; row < 3; row++) {
            for (int i = -3; i <= 3; i++) {
                double x = i * 1.25 * Math.min(spread, 1.1);
                double y = -8.3 + row * 1.9, z = Math.min(4.4, 1.2 + row * 1.5);
                double up = 6 - row * 12;
                double len = length * (1 - .14 * Math.abs(i) / 3.0) - row * 1.5 + ((i + row) & 1) * 2.5;
                s.lock(x, y, z, up, i * 6 * spread, len, 3.8 - row * .3, fall - up, 0, .45, .25);
            }
        }
        s.pair(4.4, -7, 1.2, -10, 60, length * .4, 3.2, fall + 30, -20, .45, .3);
    }
}
