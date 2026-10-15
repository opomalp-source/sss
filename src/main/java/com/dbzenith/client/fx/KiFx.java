package com.dbzenith.client.fx;

import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.KiLook;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;

/**
 * Client side of the ki attacks (the ki attack rework): what each attack does from tick to tick on the client (its
 * trail, the motes and sparks it sheds), and its look ({@link KiLook}) from the technique it was fired as.
 */
public final class KiFx {
    private static final KiLook PLAIN = new KiLook();

    private KiFx() {}

    /** The attack's look, by the technique it was fired as (the plain look if unknown, e.g. a Ki Creator design). */
    public static KiLook look(KiBlastEntity b) {
        Technique t = Techniques.byId(b.getTechnique());
        return t == null ? PLAIN : t.look();
    }

    /** A beam let go: a burst of sparks off the hands and, for the one who fired it, a kick of the camera and a flash. */
    public static void beamFired(com.dbzenith.skill.KiBeamEntity beam) {
        Minecraft mc = Minecraft.getInstance();
        float w = Math.max(0.4f, beam.getWidth());
        KiParticles.burst(beam.position(), beam.getColor(), (int) (24 + 16 * w), 0.25f + 0.1f * w, 0.8f + 0.4f * w);
        if (beam.getOwner() == mc.player) {
            CameraFx.kick(0.5f);
            CameraFx.flash(FxDraw.mix(beam.getColor(), 0xFFFFFF, 0.5f), 0.25f);
        }
        CameraFx.shakeAt(beam.position(), 0.2f + 0.1f * w, 24);
    }

    /** A ball just thrown: its flight sound starts, and a barrage's shots each flash at the palm with a quick zap (phase 5). */
    private static void fired(KiBlastEntity b) {
        com.dbzenith.client.ClientSounds.travel(b);                            // its hum and rush in flight (a disc's whine), now that its kind is known
        Technique t = Techniques.byId(b.getTechnique());
        if (t == null || t.count() <= 1 || t.has(Technique.PLACED) || t.has(Technique.RAIN)) return;
        net.minecraft.world.phys.Vec3 at = b.position().add(0, b.getBbHeight() / 2, 0);
        KiParticles.burst(at, b.getColor(), 7, 0.12f, 0.45f);
        if (b.level() instanceof net.minecraft.client.multiplayer.ClientLevel level)
            level.playLocalSound(at.x, at.y, at.z, com.dbzenith.registry.ModSounds.KI_ZAP.get(), net.minecraft.sounds.SoundSource.PLAYERS,
                    0.55f, 0.9f + level.random.nextFloat() * 0.3f, false);
    }

    /** One client tick of a beam: its caster's view rumbles while it pours out, harder for a wider beam (phase 7). */
    public static void beamTick(com.dbzenith.skill.KiBeamEntity beam) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && beam.getOwner() == mc.player) CameraFx.rumble(0.25f + 0.1f * Math.min(3, beam.getWidth()));
        if (beam.clashRole() == 1) clashTick(beam);
    }

    private static final java.util.Set<Integer> CLASH_SOUNDS = new java.util.HashSet<>();

    /**
     * A beam clash (phase 8), each tick from the leading beam: sparks spraying out across the beams in both colours, the
     * ground below answering, everyone near feeling it, and its grinding roar.
     */
    private static void clashTick(com.dbzenith.skill.KiBeamEntity beam) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.phys.Vec3 dir = beam.direction(), at = beam.position().add(dir.scale(beam.getLength()));
        float held = Math.min(1f, beam.clashAge() / (float) com.dbzenith.skill.BeamStruggle.MAX_TICKS);
        float r = Math.max(0.6f, beam.getWidth()) * (1.4f + 1.3f * held);
        RandomSource rnd = beam.level().random;
        net.minecraft.world.phys.Vec3 p = dir.cross(Math.abs(dir.y) > 0.9 ? new net.minecraft.world.phys.Vec3(1, 0, 0) : new net.minecraft.world.phys.Vec3(0, 1, 0)).normalize(),
                q = dir.cross(p).normalize();
        int n = KiParticles.scaled(4 + (int) (4 * held));
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, v = 0.35 + rnd.nextDouble() * 0.4;
            net.minecraft.world.phys.Vec3 out = p.scale(Math.cos(a)).add(q.scale(Math.sin(a)));
            int col = rnd.nextBoolean() ? beam.getColor() : beam.clashColor();
            KiParticles.add(rnd.nextInt(3) == 0 ? KiParticles.EMBER : KiParticles.SPARK, at.x + out.x * r, at.y + out.y * r, at.z + out.z * r,
                    out.x * v + dir.x * (rnd.nextDouble() - 0.5) * 0.2, out.y * v + 0.04, out.z * v + dir.z * (rnd.nextDouble() - 0.5) * 0.2,
                    0.5f + rnd.nextFloat() * 0.5f, FxDraw.mix(col, 0xFFFFFF, rnd.nextFloat() * 0.5f), 8 + rnd.nextInt(10), 0.9f, 0.02f);
        }
        BigSphere.tick(beam.level(), at, r, beam.getColor(), held > 0.3f);       // the ground below answers
        if (mc.player != null) {
            double d = mc.player.position().distanceTo(at);
            if (d < 28) CameraFx.rumble((0.2f + 0.3f * held) * (float) (1 - d / 28));
        }
        if (CLASH_SOUNDS.add(beam.getId())) com.dbzenith.client.ClientSounds.clash(beam);
        if (CLASH_SOUNDS.size() > 16) CLASH_SOUNDS.clear();
    }

    /** One client tick of a ki blast: remember where it is (the trail), shed motes and the odd spark. */
    public static void blastTick(KiBlastEntity b) {
        double cx = b.getX(), cy = b.getY() + b.getBbHeight() / 2, cz = b.getZ();
        int max = b.trail.length / 3;
        if (b.trailCount == max) System.arraycopy(b.trail, 3, b.trail, 0, b.trail.length - 3);
        else b.trailCount++;
        int i = (b.trailCount - 1) * 3;
        b.trail[i] = cx;
        b.trail[i + 1] = cy;
        b.trail[i + 2] = cz;
        Minecraft mc = Minecraft.getInstance();
        KiLook look = look(b);
        if (b.trailCount == 1) fired(b);                                     // its first tick here (its data has arrived by now)
        if (KiEnergy.available() && (b.isSpiritBomb() || BigSphere.style(look, b.getSize()) >= 0)) {   // a big sphere: the ground answers it (phase 4)
            BigSphere.tick(b.level(), new net.minecraft.world.phys.Vec3(cx, cy, cz), b.getSize() * 0.5f, b.getColor(), b.getSize() >= 3f || b.isSpiritBomb());
            return;
        }
        if (mc.player == null || b.isSpiritBomb() || mc.player.distanceToSqr(cx, cy, cz) > 96 * 96) return;
        RandomSource r = b.level().random;
        float s = b.getSize();
        int c = b.getColor();
        int motes = KiParticles.scaled(Math.round((1 + s * 2) * look.sparks));
        for (int k = 0; k < motes; k++) {                                    // motes peeling off behind it
            double ox = (r.nextDouble() - 0.5) * s * 0.6, oy = (r.nextDouble() - 0.5) * s * 0.6, oz = (r.nextDouble() - 0.5) * s * 0.6;
            var v = b.getDeltaMovement();
            KiParticles.add(KiParticles.MOTE, cx + ox - v.x * r.nextDouble(), cy + oy - v.y * r.nextDouble(), cz + oz - v.z * r.nextDouble(),
                    (r.nextDouble() - 0.5) * 0.03, (r.nextDouble() - 0.3) * 0.03, (r.nextDouble() - 0.5) * 0.03,
                    s * (0.45f + r.nextFloat() * 0.4f), FxDraw.mix(c, 0xFFFFFF, r.nextFloat() * 0.4f), 8 + r.nextInt(10), 0.9f, -0.002f);
        }
        if (b.getStyle() == Technique.Style.DISK) {                          // a disc sheds sparks off its rim, flung along its spin
            for (int k = 0; k < 2; k++) {
                double a = r.nextDouble() * Math.PI * 2, rr = s * 0.65;
                KiParticles.add(KiParticles.SPARK, cx + Math.cos(a) * rr, cy, cz + Math.sin(a) * rr, -Math.sin(a) * 0.25, 0.02, Math.cos(a) * 0.25,
                        s * 0.5f, FxDraw.mix(c, 0xFFFFFF, 0.6f), 4 + r.nextInt(4), 0.8f, 0.01f);
            }
        }
        if (b.seeking() >= 0 && r.nextFloat() < 0.6f) {                      // a homing orb trails motes that swirl round its path
            double a = b.tickCount * 0.5;
            KiParticles.add(KiParticles.MOTE, cx + Math.cos(a) * s * 0.9, cy + Math.sin(a) * s * 0.9, cz + Math.sin(a) * s * 0.9, 0, 0, 0,
                    s * 0.5f, FxDraw.mix(c, 0xFFFFFF, 0.5f), 10, 0.9f, 0);
        }
        if (r.nextFloat() < 0.35f * look.sparks) {                          // a crackle of sparks off its skin
            double ux = r.nextGaussian(), uy = r.nextGaussian(), uz = r.nextGaussian(), len = Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6;
            KiParticles.add(KiParticles.SPARK, cx + ux / len * s * 0.4, cy + uy / len * s * 0.4, cz + uz / len * s * 0.4,
                    ux / len * 0.12, uy / len * 0.12, uz / len * 0.12, s * 0.8f, 0xFFFFFF, 4 + r.nextInt(4), 0.8f, 0);
        }
    }
}
