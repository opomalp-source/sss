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
