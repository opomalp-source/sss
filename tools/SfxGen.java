import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleUnaryOperator;

/**
 * Every Dragon Block Zenith sound, synthesized from scratch (oscillators, noise, filters, envelopes, saturation and a
 * small room reverb): no samples, no recordings. Writes mono 44.1 kHz WAVs to build/sfx, encodes them to Ogg Vorbis
 * with ffmpeg (libvorbis) into assets/dbzenith/sounds, and writes sounds.json with every variant.
 * <p>
 * Usage: java tools/SfxGen.java [path to ffmpeg]   (default: $FFMPEG, then ffmpeg on the PATH)
 */
public final class SfxGen {
    static final int SR = 44100;
    static final String OUT = "src/main/resources/assets/dbzenith/";
    static final Random RNG = new Random(0xDB2);
    static final Map<String, List<String>> EVENTS = new LinkedHashMap<>();
    static String ffmpeg;

    public static void main(String[] args) throws Exception {
        ffmpeg = args.length > 0 ? args[0] : System.getenv().getOrDefault("FFMPEG", "ffmpeg");
        new File("build/sfx").mkdirs();
        new File(OUT + "sounds").mkdirs();
        for (int v = 0; v < 4; v++) save("punch_light", v, punchLight(v));
        for (int v = 0; v < 3; v++) save("punch_heavy", v, punchHeavy(v));
        for (int v = 0; v < 4; v++) save("whoosh", v, whoosh(v));
        for (int v = 0; v < 3; v++) save("ki_fire", v, kiFire(v));
        for (int v = 0; v < 3; v++) save("ki_hit", v, kiHit(v));
        for (int v = 0; v < 3; v++) save("explosion", v, explosion(v, 1.2));
        for (int v = 0; v < 2; v++) save("explosion_big", v, explosion(v + 7, 2.6));
        for (int v = 0; v < 2; v++) save("beam_fire", v, beamFire(v));
        for (int v = 0; v < 3; v++) save("guard_block", v, guardBlock(v));
        for (int v = 0; v < 2; v++) save("parry", v, parry(v));
        for (int v = 0; v < 2; v++) save("guard_break", v, guardBreak(v));
        for (int v = 0; v < 2; v++) save("deflect", v, deflect(v));
        for (int v = 0; v < 3; v++) save("dash", v, dash(v));
        for (int v = 0; v < 2; v++) save("vanish", v, vanish(v));
        save("teleport", 0, teleport());
        for (int v = 0; v < 2; v++) save("powerup", v, powerup(v));
        for (int v = 0; v < 2; v++) save("transform", v, transform(v));
        save("power_down", 0, powerDown());
        save("aura_charge", 0, loop(auraCharge(), 0.25));
        save("aura_hum", 0, loop(auraHum(), 0.25));
        save("aura_calm", 0, loop(auraCalm(), 0.25));
        save("kaioken", 0, kaioken());
        save("zenkai", 0, zenkai());
        for (int v = 0; v < 2; v++) save("flight", v, flight(v));
        for (int v = 0; v < 2; v++) save("skill", v, skill(v));
        for (int v = 0; v < 2; v++) save("ui_click", v, uiClick(v));
        save("ui_open", 0, uiOpen());
        for (int v = 0; v < 2; v++) save("stun", v, stun(v));
        for (int v = 0; v < 2; v++) save("land", v, land(v));
        for (int v = 0; v < 2; v++) save("hit_crit", v, hitCrit(v));
        for (int v = 0; v < 2; v++) save("counter_hit", v, counterHit(v));
        for (int v = 0; v < 2; v++) save("impact_boom", v, impactBoom(v));
        for (int v = 0; v < 2; v++) save("hit_guarded", v, hitGuarded(v));
        writeSoundsJson();
        System.out.println("SfxGen done: " + EVENTS.size() + " events");
    }

    // ================================================================== sounds

    static double[] punchLight(int v) {
        double[] out = buf(0.2);
        double f0 = 120 + v * 14;
        double[] body = sine(out.length, t -> f0 * Math.exp(-t * 18) + 55);
        mul(body, env(out.length, 0.001, 0.055));
        double[] slap = bandpass(noise(out.length), t -> 900 + v * 120, 1.2);
        mul(slap, env(out.length, 0.0005, 0.03));
        double[] click = highpass(noise(out.length), 2500);
        mul(click, env(out.length, 0.0002, 0.006));
        mix(out, body, 0.9);
        mix(out, slap, 0.6);
        mix(out, click, 0.35);
        saturate(out, 1.6);
        return room(out, 0.08, 0.12);
    }

    static double[] punchHeavy(int v) {
        double[] out = buf(0.55);
        double[] body = sine(out.length, t -> 85 * Math.exp(-t * 9) + 38 + v * 4);
        mul(body, env(out.length, 0.001, 0.16));
        double[] crack = bandpass(noise(out.length), t -> 1800 + v * 300, 1.0);
        mul(crack, env(out.length, 0.0003, 0.04));
        double[] air = bandpass(noise(out.length), t -> 600 + 1500 * Math.exp(-t * 6), 0.8);
        mul(air, env(out.length, 0.01, 0.18));
        mix(out, body, 1.0);
        mix(out, crack, 0.7);
        mix(out, air, 0.35);
        saturate(out, 2.6);
        return room(out, 0.12, 0.2);
    }

    static double[] whoosh(int v) {
        double len = 0.2 + v * 0.03;
        double[] out = buf(len);
        double peak = 1400 + v * 350;
        double[] n = bandpass(noise(out.length), t -> 350 + peak * Math.sin(Math.PI * Math.min(1, t / len)), 1.6);
        mul(n, shape(out.length, t -> Math.pow(Math.sin(Math.PI * t / len), 1.6)));
        mix(out, n, 1.0);
        return out;
    }

    static double[] kiFire(int v) {
        double[] out = buf(0.45);
        double start = 1500 + v * 250, end = 320 + v * 40;
        DoubleUnaryOperator f = t -> end + (start - end) * Math.exp(-t * 11) + 18 * Math.sin(2 * Math.PI * 30 * t);
        double[] tone = add(saw(out.length, f, 0.35), sine(out.length, f));
        tone = lowpass(tone, t -> 5000 * Math.exp(-t * 4) + 600);
        mul(tone, env(out.length, 0.004, 0.22));
        double[] hiss = bandpass(noise(out.length), t -> 3000 + 1500 * Math.exp(-t * 8), 1.0);
        mul(hiss, env(out.length, 0.002, 0.12));
        double[] thump = sine(out.length, t -> 140 * Math.exp(-t * 20) + 60);
        mul(thump, env(out.length, 0.001, 0.05));
        mix(out, tone, 0.7);
        mix(out, hiss, 0.4);
        mix(out, thump, 0.6);
        saturate(out, 1.4);
        return room(out, 0.1, 0.15);
    }

    static double[] kiHit(int v) {
        double[] out = buf(0.6);
        double[] blast = lowpass(noise(out.length), t -> 6000 * Math.exp(-t * 9) + 500);
        mul(blast, env(out.length, 0.001, 0.25));
        double[] thud = sine(out.length, t -> 100 * Math.exp(-t * 10) + 55 + v * 6);
        mul(thud, env(out.length, 0.001, 0.12));
        double[] sizzle = highpass(noise(out.length), 4000);
        mul(sizzle, crackle(out.length, 0.25, v));
        mul(sizzle, env(out.length, 0.01, 0.2));
        mix(out, blast, 0.8);
        mix(out, thud, 0.9);
        mix(out, sizzle, 0.3);
        saturate(out, 1.8);
        return room(out, 0.15, 0.2);
    }

    static double[] explosion(int v, double len) {
        double[] out = buf(len);
        double[] rumble = lowpass(brown(out.length), t -> 1400 * Math.exp(-t * 3.5) + 140);
        mul(rumble, env(out.length, 0.002, len * 0.45));
        double[] crack = highpass(noise(out.length), 1200);
        mul(crack, env(out.length, 0.0005, 0.05));
        double[] sub = sine(out.length, t -> 55 * Math.exp(-t * 2) + 32 + v);
        mul(sub, env(out.length, 0.002, len * 0.3));
        double[] debris = bandpass(noise(out.length), t -> 2200, 0.8);
        mul(debris, crackle(out.length, 0.15, v + 3));
        mul(debris, env(out.length, 0.05, len * 0.35));
        mix(out, rumble, 1.6);
        mix(out, crack, 0.7);
        mix(out, sub, 0.9);
        mix(out, debris, 0.25);
        saturate(out, 2.2);
        return room(out, 0.25, 0.35);
    }

    static double[] beamFire(int v) {
        double len = 2.6;
        double[] out = buf(len);
        double charge = 0.45;
        // the gathering whine
        double[] whine = sine(out.length, t -> 280 + 1300 * Math.min(1, t / charge));
        mul(whine, shape(out.length, t -> t < charge ? 0.35 * t / charge : 0.35 * Math.exp(-(t - charge) * 12)));
        double[] swell = bandpass(noise(out.length), t -> 600 + 2400 * Math.min(1, t / charge), 1.2);
        mul(swell, shape(out.length, t -> t < charge ? t / charge : Math.exp(-(t - charge) * 8)));
        // the roar
        double base = 105 + v * 12;
        double[] roar = add(add(saw(out.length, t -> base, 1), saw(out.length, t -> base * 1.012, 1)), saw(out.length, t -> base * 1.5, 0.6));
        roar = lowpass(roar, t -> 1800 + 600 * Math.sin(2 * Math.PI * 3 * t));
        double[] hiss = bandpass(noise(out.length), t -> 1300, 0.7);
        double[] body = add(roar, scale(hiss, 0.8));
        mul(body, shape(out.length, t -> t < charge ? 0 : Math.min(1, (t - charge) / 0.05) * (0.85 + 0.15 * Math.sin(2 * Math.PI * 17 * t))
                * Math.min(1, (len - t) / 0.4)));
        mix(out, whine, 0.6);
        mix(out, swell, 0.5);
        mix(out, body, 0.8);
        saturate(out, 1.8);
        return room(out, 0.2, 0.25);
    }

    static double[] guardBlock(int v) {
        double[] out = buf(0.3);
        double[] thud = sine(out.length, t -> 160 * Math.exp(-t * 15) + 90 + v * 10);
        mul(thud, env(out.length, 0.001, 0.07));
        double[] muff = lowpass(bandpass(noise(out.length), t -> 520 + v * 60, 1.0), t -> 2000);
        mul(muff, env(out.length, 0.0005, 0.06));
        mix(out, thud, 1.0);
        mix(out, muff, 0.8);
        saturate(out, 1.4);
        return room(out, 0.08, 0.12);
    }

    static double[] parry(int v) {
        double[] out = buf(0.9);
        double f = 1500 + v * 220;
        double[] ratios = {1, 2.76, 5.4, 8.93}, decays = {0.6, 0.35, 0.18, 0.1};
        for (int i = 0; i < ratios.length; i++) {
            double r = ratios[i];
            double[] p = sine(out.length, t -> f * r);
            mul(p, env(out.length, 0.0005, decays[i]));
            mix(out, p, 0.5 / (i + 1));
        }
        double[] click = highpass(noise(out.length), 3000);
        mul(click, env(out.length, 0.0002, 0.004));
        mix(out, click, 0.4);
        return room(out, 0.2, 0.3);
    }

    static double[] guardBreak(int v) {
        double[] out = buf(0.8);
        double[] crack = highpass(noise(out.length), 900);
        mul(crack, env(out.length, 0.0005, 0.06));
        Random r = new Random(77 + v);
        for (int i = 0; i < 26; i++) {                                   // shards ringing out
            double at = r.nextDouble() * 0.35, f = 2200 + r.nextDouble() * 4500;
            double[] ping = sine(out.length, t -> f);
            mul(ping, shape(out.length, t -> t < at ? 0 : Math.exp(-(t - at) * 40)));
            mix(out, ping, 0.12);
        }
        double[] thud = sine(out.length, t -> 110 * Math.exp(-t * 8) + 50);
        mul(thud, env(out.length, 0.001, 0.15));
        mix(out, crack, 0.8);
        mix(out, thud, 0.8);
        saturate(out, 1.5);
        return room(out, 0.2, 0.3);
    }

    static double[] deflect(int v) {
        double[] w = whoosh(v + 1), p = parry(v);
        double[] out = buf(0.9);
        mix(out, w, 0.7);
        mix(out, p, 0.6);
        return out;
    }

    static double[] dash(int v) {
        double[] out = buf(0.28);
        double[] air = bandpass(noise(out.length), t -> 800 + 3500 * Math.min(1, t / 0.12), 1.4);
        mul(air, shape(out.length, t -> Math.sin(Math.PI * Math.min(1, t / 0.28)) * Math.exp(-t * 6)));
        double[] fwump = sine(out.length, t -> 130 * Math.exp(-t * 14) + 55 + v * 5);
        mul(fwump, env(out.length, 0.002, 0.07));
        mix(out, air, 1.0);
        mix(out, fwump, 0.6);
        return out;
    }

    static double[] vanish(int v) {
        double[] out = buf(0.2);
        double[] zip = sine(out.length, t -> 1800 + 5000 * Math.min(1, t / 0.12));
        mul(zip, env(out.length, 0.002, 0.06));
        double[] tss = highpass(noise(out.length), 5000);
        mul(tss, env(out.length, 0.001, 0.05));
        mix(out, zip, 0.4 + 0.1 * v);
        mix(out, tss, 0.8);
        return room(out, 0.1, 0.15);
    }

    static double[] teleport() {
        double[] out = buf(1.2);
        double[] notes = {880, 1108.7, 1318.5, 1760};
        for (int i = 0; i < notes.length; i++) {
            double n = notes[i], at = i * 0.045;
            double[] b = add(sine(out.length, t -> n), scale(sine(out.length, t -> n * 2.01), 0.3));
            mul(b, shape(out.length, t -> t < at ? 0 : Math.exp(-(t - at) * 4) * (0.8 + 0.2 * Math.sin(2 * Math.PI * 9 * t))));
            mix(out, b, 0.25);
        }
        double[] air = highpass(noise(out.length), 6000);
        mul(air, env(out.length, 0.01, 0.2));
        mix(out, air, 0.15);
        return room(out, 0.35, 0.45);
    }

    static double[] powerup(int v) {
        double[] out = buf(1.0);
        double[] rumble = lowpass(brown(out.length), t -> 260 + v * 40);
        mul(rumble, shape(out.length, t -> 0.7 + 0.3 * Math.sin(2 * Math.PI * (5 + v) * t)));
        double[] whine = sine(out.length, t -> 180 + 220 * t);
        mul(whine, shape(out.length, t -> 0.25));
        double[] crack = highpass(noise(out.length), 3000);
        mul(crack, crackle(out.length, 0.08, v + 11));
        mix(out, rumble, 1.8);
        mix(out, whine, 0.3);
        mix(out, crack, 0.15);
        fade(out, 0.03, 0.15);
        saturate(out, 1.6);
        return out;
    }

    static double[] transform(int v) {
        double len = 2.4;
        double[] out = buf(len);
        double[] swell = bandpass(noise(out.length), t -> 400 + 3000 * Math.min(1, t / 0.35), 1.0);
        mul(swell, shape(out.length, t -> t < 0.35 ? Math.pow(t / 0.35, 2) : Math.exp(-(t - 0.35) * 10)));
        double[] boom = explosion(v + 20, len);
        double[] delayed = new double[out.length];
        int off = (int) (0.33 * SR);
        for (int i = off; i < out.length; i++) delayed[i] = boom[i - off];
        double[] shimmer = new double[out.length];
        for (double r : new double[]{1, 1.25, 1.5, 2}) {
            double f = 900 * r;
            double[] s = sine(out.length, t -> f * (1 + 0.004 * Math.sin(2 * Math.PI * 6 * t)));
            mul(s, shape(out.length, t -> t < 0.33 ? 0 : Math.exp(-(t - 0.33) * 1.6)));
            mix(shimmer, s, 0.15);
        }
        double[] roar = lowpass(saw(out.length, t -> 78 + v * 6, 1), t -> 900);
        mul(roar, shape(out.length, t -> t < 0.33 ? 0 : Math.exp(-(t - 0.33) * 2.2)));
        mix(out, swell, 0.8);
        mix(out, delayed, 0.9);
        mix(out, shimmer, 0.7);
        mix(out, roar, 0.4);
        saturate(out, 1.6);
        return room(out, 0.3, 0.4);
    }

    static double[] powerDown() {
        double[] out = buf(1.1);
        double[] tone = sine(out.length, t -> 150 + 450 * Math.exp(-t * 3));
        mul(tone, env(out.length, 0.01, 0.6));
        double[] air = lowpass(noise(out.length), t -> 3000 * Math.exp(-t * 3) + 200);
        mul(air, env(out.length, 0.01, 0.5));
        mix(out, tone, 0.5);
        mix(out, air, 0.6);
        return room(out, 0.25, 0.3);
    }

    static double[] auraCharge() {
        double[] out = buf(2.25);
        double[] rumble = lowpass(brown(out.length), t -> 420);
        double[] flame = bandpass(noise(out.length), t -> 1500, 0.7);
        mul(flame, crackle(out.length, 0.6, 5));
        double[] hum = add(sine(out.length, t -> 110), scale(sine(out.length, t -> 220.5), 0.5));
        mul(hum, shape(out.length, t -> 0.7 + 0.3 * Math.sin(2 * Math.PI * 4 * t)));
        double[] sparks = highpass(noise(out.length), 5000);
        mul(sparks, crackle(out.length, 0.06, 9));
        mix(out, rumble, 2.2);
        mix(out, flame, 0.5);
        mix(out, hum, 0.18);
        mix(out, sparks, 0.12);
        saturate(out, 1.5);
        return out;
    }

    static double[] auraHum() {
        double[] out = buf(2.25);
        double[] rumble = lowpass(brown(out.length), t -> 260);
        double[] flame = bandpass(noise(out.length), t -> 1100, 0.6);
        mul(flame, crackle(out.length, 0.35, 13));
        mix(out, rumble, 1.8);
        mix(out, flame, 0.25);
        return out;
    }

    static double[] auraCalm() {
        double[] out = buf(2.25);
        for (double f : new double[]{220, 330.4, 440.7, 660}) {
            double[] s = sine(out.length, t -> f);
            mul(s, shape(out.length, t -> 0.6 + 0.4 * Math.sin(2 * Math.PI * 0.45 * t + f)));
            mix(out, s, 0.12);
        }
        double[] air = highpass(lowpass(noise(out.length), t -> 6000), 2000);
        mix(out, air, 0.08);
        return out;
    }

    static double[] kaioken() {
        double[] out = buf(1.3);
        double[] roar = add(saw(out.length, t -> 88, 1), saw(out.length, t -> 91.5, 1));
        roar = lowpass(roar, t -> 2400 * Math.exp(-t * 1.2) + 500);
        mul(roar, env(out.length, 0.03, 0.7));
        double[] burst = lowpass(noise(out.length), t -> 5000 * Math.exp(-t * 5) + 300);
        mul(burst, env(out.length, 0.002, 0.3));
        double[] crack = highpass(noise(out.length), 2500);
        mul(crack, crackle(out.length, 0.3, 21));
        mul(crack, env(out.length, 0.05, 0.6));
        mix(out, roar, 0.8);
        mix(out, burst, 0.9);
        mix(out, crack, 0.25);
        saturate(out, 3.0);
        return room(out, 0.2, 0.25);
    }

    static double[] zenkai() {
        double[] out = buf(1.8);
        double[] notes = {523.3, 659.3, 784, 1046.5, 1318.5};
        for (int i = 0; i < notes.length; i++) {
            double n = notes[i], at = i * 0.09;
            double[] b = add(sine(out.length, t -> n), scale(sine(out.length, t -> n * 3), 0.15));
            mul(b, shape(out.length, t -> t < at ? 0 : Math.exp(-(t - at) * 2.5)));
            mix(out, b, 0.22);
        }
        return room(out, 0.35, 0.4);
    }

    static double[] flight(int v) {
        double[] out = buf(0.65);
        double[] air = lowpass(noise(out.length), t -> 400 + 3000 * Math.min(1, t / 0.4));
        mul(air, shape(out.length, t -> Math.sin(Math.PI * Math.min(1, t / 0.65)) * (0.8 + 0.2 * v)));
        double[] lift = sine(out.length, t -> 200 + 300 * t);
        mul(lift, env(out.length, 0.05, 0.25));
        mix(out, air, 1.0);
        mix(out, lift, 0.15);
        return out;
    }

    static double[] skill(int v) {
        double[] out = buf(0.8);
        double root = v == 0 ? 392 : 466.2;
        for (double r : new double[]{1, 1.26, 1.5, 2}) {
            double f = root * r;
            double[] s = sine(out.length, t -> f);
            mul(s, shape(out.length, t -> Math.min(1, t / 0.08) * Math.exp(-t * 3.5)));
            mix(out, s, 0.2);
        }
        double[] sparkle = highpass(noise(out.length), 7000);
        mul(sparkle, crackle(out.length, 0.1, 30 + v));
        mul(sparkle, env(out.length, 0.01, 0.4));
        mix(out, sparkle, 0.2);
        return room(out, 0.25, 0.3);
    }

    static double[] uiClick(int v) {
        double[] out = buf(0.07);
        double[] tick = sine(out.length, t -> 1600 + v * 300);
        mul(tick, env(out.length, 0.0005, 0.015));
        double[] snap = bandpass(noise(out.length), t -> 3000, 1.5);
        mul(snap, env(out.length, 0.0002, 0.006));
        mix(out, tick, 0.5);
        mix(out, snap, 0.6);
        return out;
    }

    static double[] uiOpen() {
        double[] out = buf(0.32);
        double[] a = sine(out.length, t -> t < 0.09 ? 660 : 990);
        mul(a, shape(out.length, t -> (t < 0.09 ? Math.exp(-t * 30) : Math.exp(-(t - 0.09) * 14))));
        mix(out, a, 0.5);
        return room(out, 0.15, 0.2);
    }

    static double[] stun(int v) {
        double[] out = buf(0.65);
        double[] zaps = bandpass(noise(out.length), t -> 3200 + v * 400, 2.0);
        mul(zaps, crackle(out.length, 0.4, 40 + v));
        double[] buzz = saw(out.length, t -> 60, 1);
        buzz = lowpass(buzz, t -> 1200);
        mul(buzz, shape(out.length, t -> 0.5 + 0.5 * Math.sin(2 * Math.PI * 23 * t)));
        mix(out, zaps, 1.0);
        mix(out, buzz, 0.25);
        fade(out, 0.005, 0.2);
        saturate(out, 1.5);
        return out;
    }

    static double[] land(int v) {
        double[] out = buf(0.55);
        double[] thud = sine(out.length, t -> 70 * Math.exp(-t * 7) + 38 + v * 5);
        mul(thud, env(out.length, 0.001, 0.18));
        double[] debris = lowpass(noise(out.length), t -> 900);
        mul(debris, crackle(out.length, 0.3, 50 + v));
        mul(debris, env(out.length, 0.005, 0.25));
        mix(out, thud, 1.0);
        mix(out, debris, 0.6);
        saturate(out, 1.8);
        return room(out, 0.12, 0.2);
    }


    // ---------------------------------------------------------------- CX-19e hit layers

    /** A critical blow: a sharp, bright crack over a short metallic ring. */
    static double[] hitCrit(int v) {
        double[] out = buf(0.5);
        double[] crack = highpass(noise(out.length), 1800 + v * 300);
        mul(crack, env(out.length, 0.0002, 0.025));
        double[] snap = bandpass(noise(out.length), t -> 4200 - t * 3000, 1.6);
        mul(snap, env(out.length, 0.0005, 0.05));
        double[] ring = sine(out.length, t -> 2300 + v * 180);
        mul(ring, env(out.length, 0.001, 0.14));
        double[] ring2 = sine(out.length, t -> (2300 + v * 180) * 2.41);
        mul(ring2, env(out.length, 0.001, 0.08));
        mix(out, crack, 0.9);
        mix(out, snap, 0.7);
        mix(out, ring, 0.35);
        mix(out, ring2, 0.18);
        saturate(out, 1.4);
        return room(out, 0.14, 0.18);
    }

    /** A counter: a quick rising sting into a hit. */
    static double[] counterHit(int v) {
        double[] out = buf(0.7);
        double[] rise = saw(out.length, t -> 330 * Math.pow(2, Math.min(t, 0.12) / 0.12 * 1.0) * (1 + v * 0.06), 0.6);
        rise = lowpass(rise, t -> 2600);
        mul(rise, shape(out.length, t -> t < 0.12 ? t / 0.12 : Math.exp(-(t - 0.12) * 14)));
        double[] chord = add(sine(out.length, t -> 660 * (1 + v * 0.06)), sine(out.length, t -> 990 * (1 + v * 0.06)));
        mul(chord, shape(out.length, t -> t < 0.12 ? 0 : Math.exp(-(t - 0.12) * 6)));
        double[] thump = sine(out.length, t -> t < 0.12 ? 0 : 90 * Math.exp(-(t - 0.12) * 10) + 42);
        mul(thump, shape(out.length, t -> t < 0.12 ? 0 : Math.exp(-(t - 0.12) * 7)));
        mix(out, rise, 0.45);
        mix(out, chord, 0.3);
        mix(out, thump, 0.9);
        saturate(out, 1.5);
        return room(out, 0.2, 0.25);
    }

    /** Under a heavy blow: a deep sub boom with a little air. */
    static double[] impactBoom(int v) {
        double[] out = buf(0.9);
        double[] sub = sine(out.length, t -> 52 * Math.exp(-t * 4) + 30 + v * 3);
        mul(sub, env(out.length, 0.002, 0.32));
        double[] air = lowpass(brown(out.length), t -> 400);
        mul(air, env(out.length, 0.004, 0.25));
        mix(out, sub, 1.0);
        mix(out, air, 0.5);
        saturate(out, 2.0);
        return room(out, 0.18, 0.35);
    }

    /** A blow on a raised guard: a dull, muffled thud. */
    static double[] hitGuarded(int v) {
        double[] out = buf(0.3);
        double[] thud = sine(out.length, t -> 140 * Math.exp(-t * 20) + 70 + v * 8);
        mul(thud, env(out.length, 0.001, 0.07));
        double[] pad = lowpass(noise(out.length), t -> 700);
        mul(pad, env(out.length, 0.001, 0.04));
        mix(out, thud, 1.0);
        mix(out, pad, 0.5);
        saturate(out, 1.3);
        return room(out, 0.06, 0.1);
    }
    // ================================================================== building blocks

    static double[] buf(double seconds) {
        return new double[(int) (seconds * SR)];
    }

    static double[] noise(int n) {
        double[] b = new double[n];
        for (int i = 0; i < n; i++) b[i] = RNG.nextDouble() * 2 - 1;
        return b;
    }

    /** Brown (red) noise: integrated white noise with a leak, for rumbles. */
    static double[] brown(int n) {
        double[] b = new double[n];
        double v = 0;
        for (int i = 0; i < n; i++) {
            v = v * 0.995 + (RNG.nextDouble() * 2 - 1) * 0.08;
            b[i] = v;
        }
        return normalizeTo(b, 1);
    }

    static double[] sine(int n, DoubleUnaryOperator freq) {
        double[] b = new double[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            ph += 2 * Math.PI * freq.applyAsDouble(i / (double) SR) / SR;
            b[i] = Math.sin(ph);
        }
        return b;
    }

    /** A band-limited-enough saw (a few harmonics), {@code bright} 0..1 for how many. */
    static double[] saw(int n, DoubleUnaryOperator freq, double bright) {
        double[] b = new double[n];
        double ph = 0;
        int harmonics = (int) (4 + 12 * bright);
        for (int i = 0; i < n; i++) {
            double f = freq.applyAsDouble(i / (double) SR);
            ph += 2 * Math.PI * f / SR;
            double s = 0;
            for (int h = 1; h <= harmonics && h * f < SR / 2.5; h++) s += Math.sin(ph * h) / h;
            b[i] = s * 0.6;
        }
        return b;
    }

    /** Attack then exponential decay ({@code decay} = time to fall to about 1/e^3). */
    static double[] env(int n, double attack, double decay) {
        return shape(n, t -> t < attack ? t / attack : Math.exp(-(t - attack) * 3 / decay));
    }

    static double[] shape(int n, DoubleUnaryOperator f) {
        double[] b = new double[n];
        for (int i = 0; i < n; i++) b[i] = f.applyAsDouble(i / (double) SR);
        return b;
    }

    /** Random sparse bursts: fire crackle, sparks, debris ({@code density} 0..1). */
    static double[] crackle(int n, double density, int seed) {
        Random r = new Random(seed * 31L + 7);
        double[] b = new double[n];
        double level = 0;
        for (int i = 0; i < n; i++) {
            if (r.nextDouble() < density * 0.004) level = 0.4 + r.nextDouble() * 0.6;
            level *= 0.9985;
            b[i] = level;
        }
        return b;
    }

    static double[] lowpass(double[] in, DoubleUnaryOperator cutoff) {
        double[] out = new double[in.length];
        double y = 0;
        for (int i = 0; i < in.length; i++) {
            double fc = Math.min(SR * 0.45, Math.max(20, cutoff.applyAsDouble(i / (double) SR)));
            double a = 1 - Math.exp(-2 * Math.PI * fc / SR);
            y += a * (in[i] - y);
            out[i] = y;
        }
        // a second pole for a steeper slope
        double[] out2 = new double[in.length];
        y = 0;
        for (int i = 0; i < in.length; i++) {
            double fc = Math.min(SR * 0.45, Math.max(20, cutoff.applyAsDouble(i / (double) SR)));
            double a = 1 - Math.exp(-2 * Math.PI * fc / SR);
            y += a * (out[i] - y);
            out2[i] = y;
        }
        return out2;
    }

    static double[] highpass(double[] in, double cutoff) {
        double[] low = lowpass(in, t -> cutoff);
        double[] out = new double[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] - low[i];
        return out;
    }

    /** RBJ band-pass biquad with a centre that may move over time. */
    static double[] bandpass(double[] in, DoubleUnaryOperator centre, double q) {
        double[] out = new double[in.length];
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int i = 0; i < in.length; i++) {
            double f = Math.min(SR * 0.45, Math.max(30, centre.applyAsDouble(i / (double) SR)));
            double w = 2 * Math.PI * f / SR, alpha = Math.sin(w) / (2 * q);
            double b0 = alpha, b2 = -alpha, a0 = 1 + alpha, a1 = -2 * Math.cos(w), a2 = 1 - alpha;
            double y = (b0 * in[i] + b2 * x2 - a1 * y1 - a2 * y2) / a0;
            x2 = x1;
            x1 = in[i];
            y2 = y1;
            y1 = y;
            out[i] = y;
        }
        return normalizeTo(out, 1);
    }

    static void mul(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) a[i] *= b[i];
    }

    static void mix(double[] into, double[] what, double gain) {
        for (int i = 0; i < Math.min(into.length, what.length); i++) into[i] += what[i] * gain;
    }

    static double[] add(double[] a, double[] b) {
        double[] o = a.clone();
        mix(o, b, 1);
        return o;
    }

    static double[] scale(double[] a, double g) {
        double[] o = a.clone();
        for (int i = 0; i < o.length; i++) o[i] *= g;
        return o;
    }

    static void saturate(double[] a, double drive) {
        for (int i = 0; i < a.length; i++) a[i] = Math.tanh(a[i] * drive) / Math.tanh(drive);
    }

    static void fade(double[] a, double in, double out) {
        int ni = (int) (in * SR), no = (int) (out * SR);
        for (int i = 0; i < ni && i < a.length; i++) a[i] *= i / (double) ni;
        for (int i = 0; i < no && i < a.length; i++) a[a.length - 1 - i] *= i / (double) no;
    }

    /** A small Schroeder room: four combs into two all-passes, mixed under the dry sound, with a tail added. */
    static double[] room(double[] dry, double wet, double size) {
        int tail = (int) (size * 2.5 * SR);
        dry = dry.clone();
        fade(dry, 0, 0.01);                                              // no click where a still-ringing sound is cut
        double[] in = Arrays.copyOf(dry, dry.length + tail);
        double[] rev = new double[in.length];
        int[] combs = {1557, 1617, 1491, 1422};
        double fb = 0.7 + size * 0.5;
        for (int c : combs) {
            int d = (int) (c * (0.5 + size));
            double[] line = new double[d];
            int p = 0;
            for (int i = 0; i < in.length; i++) {
                double y = line[p];
                line[p] = in[i] + y * Math.min(0.92, fb);
                p = (p + 1) % d;
                rev[i] += y * 0.25;
            }
        }
        for (int d : new int[]{225, 556}) {
            double[] line = new double[d];
            int p = 0;
            for (int i = 0; i < rev.length; i++) {
                double buf = line[p], x = rev[i];
                double y = -x + buf;
                line[p] = x + buf * 0.5;
                p = (p + 1) % d;
                rev[i] = y;
            }
        }
        double[] out = new double[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] + rev[i] * wet;
        int cut = out.length;                                            // trim silence off the tail
        while (cut > dry.length && Math.abs(out[cut - 1]) < 1e-4) cut--;
        out = Arrays.copyOf(out, cut);
        fade(out, 0, Math.min(0.05, cut / (double) SR / 4));
        return out;
    }

    /** Make a loop seamless: the end crossfades into the start. */
    static double[] loop(double[] a, double seconds) {
        int x = (int) (seconds * SR);
        double[] out = Arrays.copyOf(a, a.length - x);
        for (int i = 0; i < x; i++) {
            double t = i / (double) x;
            out[i] = a[i] * t + a[a.length - x + i] * (1 - t);
        }
        return out;
    }

    static double[] normalizeTo(double[] a, double peak) {
        double m = 1e-9;
        for (double v : a) m = Math.max(m, Math.abs(v));
        for (int i = 0; i < a.length; i++) a[i] *= peak / m;
        return a;
    }

    // ================================================================== output

    static void save(String event, int variant, double[] samples) throws Exception {
        normalizeTo(samples, 0.89);
        String name = event + (variant > 0 || event.matches("punch_light|punch_heavy|whoosh|ki_fire|ki_hit|explosion|explosion_big|beam_fire|guard_block|parry|guard_break|deflect|dash|vanish|powerup|transform|flight|skill|ui_click|stun|land|hit_crit|counter_hit|impact_boom|hit_guarded") ? "_" + (variant + 1) : "");
        File wav = new File("build/sfx/" + name + ".wav");
        writeWav(wav, samples);
        File ogg = new File(OUT + "sounds/" + name + ".ogg");
        Process p = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", wav.getPath(), "-c:a", "libvorbis", "-q:a", "5", "-ac", "1", ogg.getPath())
                .redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new IOException("ffmpeg failed for " + name + ": " + log);
        EVENTS.computeIfAbsent(event, k -> new ArrayList<>()).add(name);
        System.out.println("sfx " + name + " (" + String.format("%.2f", samples.length / (double) SR) + "s)");
    }

    static void writeWav(File f, double[] s) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            int bytes = s.length * 2;
            out.writeBytes("RIFF");
            out.writeInt(Integer.reverseBytes(36 + bytes));
            out.writeBytes("WAVEfmt ");
            out.writeInt(Integer.reverseBytes(16));
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeInt(Integer.reverseBytes(SR));
            out.writeInt(Integer.reverseBytes(SR * 2));
            out.writeShort(Short.reverseBytes((short) 2));
            out.writeShort(Short.reverseBytes((short) 16));
            out.writeBytes("data");
            out.writeInt(Integer.reverseBytes(bytes));
            for (double v : s) out.writeShort(Short.reverseBytes((short) Math.round(Math.max(-1, Math.min(1, v)) * 32767)));
        }
    }

    static void writeSoundsJson() throws IOException {
        StringBuilder b = new StringBuilder("{\n");
        int i = 0;
        for (var e : EVENTS.entrySet()) {
            b.append("  \"").append(e.getKey()).append("\": {\n    \"subtitle\": \"subtitles.dbzenith.").append(e.getKey()).append("\",\n    \"sounds\": [");
            for (int k = 0; k < e.getValue().size(); k++) {
                if (k > 0) b.append(", ");
                b.append("\"dbzenith:").append(e.getValue().get(k)).append("\"");
            }
            b.append("]\n  }").append(++i < EVENTS.size() ? "," : "").append("\n");
        }
        b.append("}\n");
        Files.writeString(Path.of(OUT + "sounds.json"), b.toString());
    }
}
