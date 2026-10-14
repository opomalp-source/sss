package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.skill.KiLook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Big spheres (the ki attack rework, phase 4): the Spirit Bomb, the Supernova, anything thrown big. Drawn through the
 * energy shader:
 * <ul>
 *   <li>a wide corona;</li>
 *   <li>layers of shell and plasma, each turning on its own tilted axis so the surface swirls, round a white-hot core;</li>
 *   <li>sparks racing round it on tilted orbits;</li>
 *   <li>by its character ({@link KiLook#sphere}): streams of energy flowing in from all round while it gathers (the
 *       Spirit Bomb), or flares looping off its surface (the Supernova).</li>
 * </ul>
 * Near the ground it lights the ground below, and the ground answers: dust and stones lifting, the camera trembling.
 */
public final class BigSphere {
    public static final int PLAIN = 0, GATHER = 1, NOVA = 2;
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final Matrix3f R1 = new Matrix3f(), R2 = new Matrix3f(), R3 = new Matrix3f();

    private BigSphere() {}

    /** Its character for a look and a size, or -1 when it isn't drawn as a big sphere. */
    public static int style(KiLook look, float size) {
        return switch (look.sphere) {
            case "gather" -> GATHER;
            case "nova" -> NOVA;
            case "plain" -> PLAIN;
            case "none" -> -1;
            default -> size >= 2f ? PLAIN : -1;
        };
    }

    /**
     * Draws one at (x, y, z) in the pose's space, radius {@code r}; (ex, ey, ez) is the eye in the same space.
     * {@code inflow}: energy is flowing into it (it is gathering, or being charged). {@code seed} keeps two apart.
     */
    public static void draw(MultiBufferSource buffers, Matrix4f m, Matrix3f n, float x, float y, float z, float r, int c, int core, int style,
                            float t, float ex, float ey, float ez, Vector3f left, Vector3f up, int detail, float glow, boolean inflow, int seed) {
        int hot = FxDraw.mix(c, 0xFFFFFF, 0.55f);
        int deep = vivid(c);                                                    // pale colours (the Spirit Bomb's) would read as plain white
        float breathe = 1f + 0.025f * Mth.sin(t * 0.21f) + 0.012f * Mth.sin(t * 0.77f);
        float rr = r * breathe;

        // under it all, a see-through body in its colour, so it keeps its colour against a bright sky
        R1.rotationXYZ(0.35f, t * 0.012f, -0.2f);
        KiEnergy.sphere(buffers.getBuffer(KiEnergy.tint()), m, n, x, y, z, rr * 0.97f, KiEnergy.argb(deep, style == NOVA ? 165 : 145), KiEnergy.TINT, Math.max(1, detail), 0, R1);
        if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch(KiEnergy.tint());

        // flat things first: the corona, the orbiting sparks, the inflow or the flares
        VertexConsumer flat = buffers.getBuffer(KiEnergy.flat());
        KiEnergy.billboard(flat, m, n, x, y, z, left, up, rr * 3.3f * glow, KiEnergy.argb(deep, style == NOVA ? 75 : 60), KiEnergy.HALO);
        KiEnergy.billboard(flat, m, n, x, y, z, left, up, rr * 1.9f * glow, KiEnergy.argb(c, 70), KiEnergy.HALO);
        orbits(flat, m, n, x, y, z, rr, hot, t, ex, ey, ez, detail == 0 ? 2 : 3 + detail, seed);
        if (style == GATHER && inflow) inflow(flat, m, n, x, y, z, rr, FxDraw.mix(c, 0xFFFFFF, 0.5f), t, ex, ey, ez, detail == 0 ? 8 : 10 + 4 * detail, seed);
        if (style == NOVA) flares(flat, m, n, x, y, z, rr, FxDraw.mix(c, 0xFFE070, 0.45f), t, ex, ey, ez, detail == 0 ? 3 : 4 + detail, seed);
        if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch(KiEnergy.flat());

        // the body: shell layers on their own axes, swirling past each other, round the core
        VertexConsumer solid = buffers.getBuffer(KiEnergy.solid());
        int d = Math.max(1, detail);
        R1.rotationXYZ(0.35f, t * 0.012f, -0.2f);
        R2.rotationXYZ(1.05f, -t * 0.021f, 0.45f);
        R3.rotationXYZ(-0.7f, t * 0.033f, 1.2f);
        KiEnergy.sphere(solid, m, n, x, y, z, rr, KiEnergy.argb(deep, style == NOVA ? 210 : 190), KiEnergy.SHELL, d, t * 0.01f, R1);
        KiEnergy.sphere(solid, m, n, x, y, z, rr * 0.84f, KiEnergy.argb(c, 90), KiEnergy.SHELL, d, -t * 0.016f, R2);
        KiEnergy.sphere(solid, m, n, x, y, z, rr * 1.03f, KiEnergy.argb(c, 185), KiEnergy.STRANDS, d, t * 0.02f, R3);
        if (detail > 0) KiEnergy.sphere(solid, m, n, x, y, z, rr * 1.08f, KiEnergy.argb(c, 90), KiEnergy.STRANDS, d, -t * 0.03f, R2);
        KiEnergy.sphere(solid, m, n, x, y, z, rr * 0.5f, KiEnergy.argb(FxDraw.mix(core, c, 0.25f), 150), KiEnergy.CORE, 1, 0, R1);
        if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch(KiEnergy.solid());
    }

    /** Sparks racing round it, two to an orbit, each orbit tilted its own way, alternate ones turning back. */
    private static void orbits(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float r, int rgb, float t,
                               float ex, float ey, float ez, int count, int seed) {
        Vector3f a = new Vector3f(), b = new Vector3f(), nrm = new Vector3f();
        for (int i = 0; i < count; i++) {
            float tilt = hash(seed * 7 + i) * Mth.PI, turn = hash(seed * 13 + i * 3) * Mth.TWO_PI;
            nrm.set(Mth.sin(tilt) * Mth.cos(turn), Mth.cos(tilt), Mth.sin(tilt) * Mth.sin(turn));
            basis(nrm, a, b);
            float rad = r * (1.18f + 0.1f * (i % 3));
            float speed = (0.09f + 0.025f * i) * (i % 2 == 0 ? 1 : -1);
            for (int k = 0; k < 2; k++) {
                float head = t * speed + k * Mth.PI + i;
                float lx = 0, ly = 0, lz = 0;
                for (int s = 0; s <= 6; s++) {                                    // a short arc trailing behind it
                    float ang = head - Math.signum(speed) * s * 0.11f;
                    float px = x + (a.x * Mth.cos(ang) + b.x * Mth.sin(ang)) * rad;
                    float py = y + (a.y * Mth.cos(ang) + b.y * Mth.sin(ang)) * rad;
                    float pz = z + (a.z * Mth.cos(ang) + b.z * Mth.sin(ang)) * rad;
                    if (s > 0) {
                        float fa = 1 - (s - 1) / 6f, fb = 1 - s / 6f;
                        KiEnergy.ribbon(vc, m, n, lx, ly, lz, px, py, pz, ex, ey, ez, r * 0.07f * fa, r * 0.07f * fb, rgb, (int) (230 * fa), (int) (230 * fb), 0, 1, KiEnergy.RIBBON);
                    }
                    lx = px; ly = py; lz = pz;
                }
            }
        }
    }

    /** Streams of energy drawn in from all round, arriving as they fade in: the Spirit Bomb gathering. */
    private static void inflow(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float r, int rgb, float t,
                               float ex, float ey, float ez, int count, int seed) {
        for (int i = 0; i < count; i++) {
            float p = frac(t * 0.011f + i * 0.618f);                                 // 0 far out .. 1 arriving
            int cycle = (int) Math.floor(t * 0.011f + i * 0.618f);
            float h = hash(seed * 31 + i * 17 + cycle * 101), h2 = hash(seed * 37 + i * 19 + cycle * 103);
            float tilt = (float) Math.acos(1 - 2 * h), turn = h2 * Mth.TWO_PI;
            float fade = Mth.sin(p * Mth.PI);
            float dist = r * (1.1f + 3.2f * (1 - p)), len = r * (0.5f + 0.9f * (1 - p));
            float lx = 0, ly = 0, lz = 0;
            for (int s = 0; s <= 4; s++) {
                float dd = dist + len * s / 4f;
                float swirl = turn + 0.5f * (dd / r - 1);                             // curling in as it comes
                float px = x + Mth.sin(tilt) * Mth.cos(swirl) * dd, py = y + Mth.cos(tilt) * dd, pz = z + Mth.sin(tilt) * Mth.sin(swirl) * dd;
                if (s > 0) {
                    float fa = 1 - (s - 1) / 4f, fb = 1 - s / 4f;
                    KiEnergy.ribbon(vc, m, n, lx, ly, lz, px, py, pz, ex, ey, ez, r * 0.09f * fa, r * 0.09f * fb, rgb,
                            (int) (200 * fade * fa), (int) (200 * fade * fb), 0, 1, KiEnergy.RIBBON);
                }
                lx = px; ly = py; lz = pz;
            }
        }
    }

    /** Flares looping off its surface and falling back, each rising and dying in turn somewhere new: the Supernova. */
    private static void flares(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float r, int rgb, float t,
                               float ex, float ey, float ez, int count, int seed) {
        Vector3f d1 = new Vector3f(), d2 = new Vector3f(), dir = new Vector3f();
        for (int i = 0; i < count; i++) {
            float cyc = t / 36f + i * 0.37f;
            float p = frac(cyc);
            int cycle = (int) Math.floor(cyc);
            float grow = Mth.sin(p * Mth.PI);
            float th = (float) Math.acos(1 - 2 * hash(seed * 41 + i * 23 + cycle * 7)), ph = hash(seed * 43 + i * 29 + cycle * 11) * Mth.TWO_PI;
            d1.set(Mth.sin(th) * Mth.cos(ph), Mth.cos(th), Mth.sin(th) * Mth.sin(ph));
            float th2 = th + 0.45f, ph2 = ph + 0.35f;
            d2.set(Mth.sin(th2) * Mth.cos(ph2), Mth.cos(th2), Mth.sin(th2) * Mth.sin(ph2));
            float lx = 0, ly = 0, lz = 0;
            for (int s = 0; s <= 8; s++) {
                float u = s / 8f;
                d1.lerp(d2, u, dir).normalize();
                float lift = r * (1f + 0.55f * grow * Mth.sin(u * Mth.PI));
                float px = x + dir.x * lift, py = y + dir.y * lift, pz = z + dir.z * lift;
                if (s > 0) {
                    float w = r * 0.11f * (0.5f + 0.5f * Mth.sin(u * Mth.PI)) * (0.4f + 0.6f * grow);
                    KiEnergy.ribbon(vc, m, n, lx, ly, lz, px, py, pz, ex, ey, ez, w, w, rgb, (int) (230 * grow), (int) (230 * grow), u, u + 0.12f, KiEnergy.RIBBON);
                }
                lx = px; ly = py; lz = pz;
            }
        }
    }

    // ------------------------------------------------------------------ the ground answers

    /** The ground below a point within {@code reach}, or null. */
    public static Vec3 groundBelow(Level level, Vec3 at, double reach) {
        BlockHitResult hit = level.clip(new ClipContext(at, at.add(0, -reach, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    /** Its light on the ground below (the faked light): wide and bright when it hangs low, fading as it rises. */
    public static void groundGlow(PoseStack pose, MultiBufferSource buffers, Vec3 groundRel, float r, float height, int c, float strength) {
        float k = Mth.clamp(1 - height / (r * 5f), 0, 1) * strength;
        if (k <= 0.01f) return;
        var type = FxRenderTypes.additive(GLOW);
        FxDraw.plane(pose, buffers.getBuffer(type), groundRel.add(0, 0.03, 0), new Vec3(0, 1, 0), r * (1.8f + 1.2f * (1 - k)), 0, c, (int) (95 * k), LightTexture.FULL_BRIGHT);
        FxDraw.plane(pose, buffers.getBuffer(type), groundRel.add(0, 0.04, 0), new Vec3(0, 1, 0), r * 0.9f, 0, FxDraw.mix(c, 0xFFFFFF, 0.5f), (int) (70 * k), LightTexture.FULL_BRIGHT);
        if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch(type);
    }

    /**
     * Each client tick near one: the ground beneath gives up dust and stones that lift toward it, faster the lower it
     * hangs, and the camera trembles near it. Sparks spit off its skin.
     */
    public static void tick(Level level, Vec3 centre, float r, int c, boolean heavy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(centre) > 128 * 128) return;
        RandomSource rnd = level.random;
        if (rnd.nextFloat() < 0.5f) {                                             // sparks spitting off its skin
            double ux = rnd.nextGaussian(), uy = rnd.nextGaussian(), uz = rnd.nextGaussian(), l = Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6;
            KiParticles.add(KiParticles.SPARK, centre.x + ux / l * r, centre.y + uy / l * r, centre.z + uz / l * r, ux / l * 0.2, uy / l * 0.2, uz / l * 0.2,
                    r * 0.35f, 0xFFFFFF, 5 + rnd.nextInt(5), 0.85f, 0);
        }
        Vec3 ground = groundBelow(level, centre, r * 5);
        if (ground == null) return;
        float k = (float) Mth.clamp(1 - (centre.y - ground.y) / (r * 5), 0, 1);
        if (heavy) CameraFx.shakeAt(centre, 0.04f + 0.12f * k, r * 6);
        int n = KiParticles.scaled(Math.round((0.5f + 2.5f * k) * (heavy ? 1.5f : 1)));
        for (int i = 0; i < n; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2, dist = r * (0.4 + rnd.nextDouble() * 2.2);
            double px = centre.x + Math.cos(ang) * dist, pz = centre.z + Math.sin(ang) * dist;
            BlockPos below = BlockPos.containing(px, ground.y - 0.5, pz);
            BlockState state = level.getBlockState(below);
            if (state.isAir()) continue;
            double lift = 0.15 + 0.35 * k * rnd.nextDouble();
            double pull = 0.04 * k;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), px, ground.y + 0.1, pz,
                    (centre.x - px) * pull * 0.2, lift, (centre.z - pz) * pull * 0.2);
            if (rnd.nextFloat() < 0.3f) KiParticles.add(KiParticles.MOTE, px, ground.y + 0.2, pz, (centre.x - px) * 0.01, 0.05 + 0.1 * k, (centre.z - pz) * 0.01,
                    r * 0.3f, FxDraw.mix(c, 0xFFFFFF, 0.3f), 14 + rnd.nextInt(10), 0.95f, -0.004f);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void basis(Vector3f nrm, Vector3f a, Vector3f b) {
        if (Math.abs(nrm.y) < 0.9f) a.set(0, 1, 0).cross(nrm).normalize();
        else a.set(1, 0, 0).cross(nrm).normalize();
        nrm.cross(a, b).normalize();
    }

    /** The same colour, at least half saturated and bright: a pale blue-white keeps its blue. */
    static int vivid(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
        return java.awt.Color.HSBtoRGB(hsb[0], Math.max(hsb[1], 0.55f), Math.max(hsb[2], 0.85f)) & 0xFFFFFF;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** 0..1 from an integer, steady from frame to frame. */
    private static float hash(int i) {
        int h = i * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
