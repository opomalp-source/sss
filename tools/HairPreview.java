package com.dbzenith.client.hair;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.HairCode.Preset;
import com.dbzenith.client.render.HairTransformation;
import com.dbzenith.transform.Form;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Offline checks of the hair (no Minecraft window): every stock style in every look builds and draws whole; every
 * change from one look to another draws whole at every step; editor hair draws; the physics stay finite, keep roots
 * on the scalp, keep locks their length, stream back in moving air and settle; hair codes, old saved styles and the
 * transformation flashes still behave. Arg: an output folder for the report.
 */
public final class HairPreview {
    public static void main(String[] args) throws Exception {
        StringBuilder report = new StringBuilder("Hair checks\n");
        int looks = 0, changes = 0;
        for (Preset p : Preset.values()) {
            check(HairCode.strands(p).equals(HairCode.decode(p.code())), "preset code round trip: " + p);
            check(HairCodes.presetOf(p.code()) == p || p == Preset.BALD, "a preset's code is that preset: " + p);
            for (HairLook look : HairLook.values()) {
                HairShape s = HairStyles.of(p, look);
                check((p == Preset.BALD && look == HairLook.BASE) || !s.isEmpty(), "every look has hair: " + p + "/" + look);
                drawWhole(s, p + "/" + look);
                for (HairLock l : s.locks()) {
                    check(l.k().length == HairLock.KNOTS * 5, "locks have " + HairLock.KNOTS + " knots");
                    check(l.y(0) > -9.6 && l.y(0) < 1 && Math.abs(l.x(0)) < 6.5 && Math.abs(l.z(0)) < 6.5, "roots on the head: " + p + "/" + look);
                }
                looks++;
                for (HairLook from : HairLook.values()) for (float t : new float[]{.1f, .35f, .6f, .9f}) {
                    drawWhole(HairStyles.between(p, from, look, t), p + " " + from + ">" + look + " " + t);
                    changes++;
                }
            }
        }
        report.append("PASS: ").append(looks).append(" looks and ").append(changes).append(" look-to-look steps draw whole\n");

        double previous = -1;                                                       // the mane grows from the roots
        for (float t : new float[]{0, .25f, .5f, .75f, 1}) {
            double total = 0;
            for (HairLock l : HairStyles.between(Preset.SPIKY, HairLook.BASE, HairLook.SSJ3, t).locks()) total += l.length();
            check(total >= previous - 1e-6, "hair grows steadily into Super Saiyan 3 (" + total + " after " + previous + ")");
            previous = total;
        }
        report.append("PASS: Super Saiyan 3 hair grows steadily from the roots\n");

        var custom = List.of(new HairCode.Strand(HairCode.Face.TOP, 5, 2, 2, -1, 9, 2, HairCode.Bend.LIFT));
        String code = HairCode.encode(custom);
        check(HairCode.decode(code).equals(custom), "custom hair unchanged");
        check(HairCodes.presetOf(code) == null, "custom hair is drawn from its strands");
        drawWhole(HairCodes.shape(code, Form.builder("t").build()), "custom");
        drawWhole(HairCodes.shape(code, Form.builder("t").hair(Form.HairStyle.SPIKY).build()), "custom raised");
        check(HairCode.decode("garbage") == null, "reject garbage");
        check(HairCodes.presetOf("") == Preset.BALD, "no code is bald");
        java.util.Properties legacy = new java.util.Properties();
        try (var input = HairCode.class.getResourceAsStream("/assets/dbzenith/hair/legacy-presets.properties")) {
            check(input != null, "migration resource present");
            legacy.load(input);
        }
        for (String old : legacy.stringPropertyNames()) {
            Preset preset = Preset.valueOf(legacy.getProperty(old));
            check(HairCodes.presetOf(old) == preset, "an old saved style upgrades: " + preset);
        }
        report.append("PASS: editor hair, ").append(legacy.size()).append(" old saved styles\n");

        for (Preset p : new Preset[]{Preset.SPIKY, Preset.MANE, Preset.LONG, Preset.PONYTAIL}) for (HairLook look : new HairLook[]{HairLook.BASE, HairLook.SSJ, HairLook.SSJ3}) {
            HairShape s = HairStyles.of(p, look);
            float[] rest = HairSim.restOf(s), pos = rest.clone(), prev = rest.clone();
            HairSim.simulate(s, pos, prev, new float[]{0, 1, 0}, new float[]{0, 0, 0}, 0, .34f, 120);       // standing still
            double drift = 0;
            for (int i = 0; i < pos.length; i++) {
                check(Float.isFinite(pos[i]), "finite: " + p + "/" + look);
                drift = Math.max(drift, Math.abs(pos[i] - rest[i]));
            }
            check(drift < 7, "still hair stays near its style: " + p + "/" + look + " " + drift);
            for (int li = 0; li < s.locks().size(); li++) {
                int o = li * HairLock.KNOTS * 3;
                check(Math.abs(pos[o] - rest[o]) < 1e-4 && Math.abs(pos[o + 1] - rest[o + 1]) < 1e-4, "roots stay on the scalp");
                HairLock l = s.locks().get(li);
                for (int i = 1; i < HairLock.KNOTS; i++) {
                    int a = o + i * 3, b = a - 3;
                    double seg = Math.sqrt(sq(pos[a] - pos[b]) + sq(pos[a + 1] - pos[b + 1]) + sq(pos[a + 2] - pos[b + 2]));
                    double want = Math.sqrt(sq(l.x(i) - l.x(i - 1)) + sq(l.y(i) - l.y(i - 1)) + sq(l.z(i) - l.z(i - 1)));
                    check(Math.abs(seg - want) < .2, "locks keep their length (" + seg + " vs " + want + ")");
                }
            }
            float[] run = rest.clone(), runPrev = rest.clone();
            HairSim.simulate(s, run, runPrev, new float[]{0, 1, 0}, new float[]{0, 0, 10}, 0, .34f, 120);  // running: air from the front
            double back = 0;
            for (int i = 2; i < run.length; i += 3) back += run[i] - rest[i];
            check(back > 0, "running streams the hair back: " + p + "/" + look);
            float[] calm = run.clone(), calmPrev = run.clone();
            HairSim.simulate(s, calm, calmPrev, new float[]{0, 1, 0}, new float[]{0, 0, 0}, 0, .34f, 400);  // stopping: it settles
            double settled = 0;
            for (int i = 0; i < calm.length; i++) settled = Math.max(settled, Math.abs(calm[i] - pos[i]));
            check(settled < 3, "hair settles after stopping: " + p + "/" + look + " " + settled);
        }
        report.append("PASS: physics finite, roots anchored, lengths kept, streaming back when running, settling\n");
        for (Preset p : new Preset[]{Preset.SPIKY, Preset.MANE, Preset.LONG}) for (HairLook look : new HairLook[]{HairLook.BASE, HairLook.SSJ}) {
            HairShape s = HairStyles.of(p, look);                                     // whipping the head about every frame
            float[] rest = HairSim.restOf(s), pos = rest.clone(), prev = rest.clone();
            double worst = 0;
            for (int f = 0; f < 200; f++) {
                HairSim.turn(pos, prev, (f & 1) == 0 ? 70 : -70);
                HairSim.simulate(s, pos, prev, new float[]{0, 1, 0}, new float[]{(f & 1) == 0 ? 6 : -6, 0, 4}, 0, .34f, 1);
                for (int li = 0; li < s.locks().size(); li++) {
                    HairLock l = s.locks().get(li);
                    double tip = Math.sqrt(sq(pos[(li * HairLock.KNOTS + 6) * 3] - l.x(6)) + sq(pos[(li * HairLock.KNOTS + 6) * 3 + 1] - l.y(6))
                            + sq(pos[(li * HairLock.KNOTS + 6) * 3 + 2] - l.z(6)));
                    worst = Math.max(worst, tip / Math.max(1, l.length()));
                }
            }
            check(worst < .5, "whipping the head about, the hair stays near its style: " + p + "/" + look + " " + worst);
        }
        report.append("PASS: hair stays near its style when the head whips about\n");

        for (float progress : new float[]{0, .5f, 1}) {
            java.util.Set<Integer> colors = new java.util.HashSet<>();
            for (int t = 0; t < 32; t++) colors.add(HairTransformation.chargeColor(0x172534, 0xFFD34A, progress, t));
            check(colors.equals(java.util.Set.of(0x172534, 0xFFD34A)), "gold and the own colour both flash throughout a charge");
        }
        report.append("PASS: transformation flashes\n");
        File out = new File(args.length > 0 ? args[0] : "build/hair-check");
        out.mkdirs();
        Files.writeString(new File(out, "hair-validation.txt").toPath(), report);
        System.out.print(report);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }

    /** Draws a shape and checks every quad is whole: four finite corners, unit normals. */
    private static void drawWhole(HairShape s, String what) {
        Counter c = new Counter(what);
        HairRender.render(new PoseStack(), c, s, null, 15728880, 0, 0x202020, 0xFFCC00);
        check(c.vertices % 4 == 0, "whole quads: " + what);
    }

    private static final class Counter implements VertexConsumer {
        final String what;
        int vertices;

        Counter(String what) { this.what = what; }

        @Override public VertexConsumer vertex(double x, double y, double z) { check(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z), "finite: " + what); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) {
            float l = x * x + y * y + z * z;
            check(Float.isFinite(l) && Math.abs(l - 1) < .02, "unit normals: " + what);
            return this;
        }
        @Override public void endVertex() { vertices++; }
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }
}
