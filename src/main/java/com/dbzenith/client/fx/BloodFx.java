package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Blood;
import com.dbzenith.combat.Limbs;
import com.dbzenith.network.ImpactPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Blood on the client (the knocked-out rework): drops that fly and fall, and splats where they land, which dry and fade
 * over a minute. Each race's own colour ({@link Blood}); Majin goo comes in fat, slow blobs; androids leak oil and
 * spark; cyborgs bleed and spark. Fresh wounds ({@link Limbs.State#bleeding}) drip and pulse from the stump.
 * Lit by the world like anything else, not glowing. Everything lives in fixed pools.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class BloodFx {
    private static final ResourceLocation DROP = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/blood_drop.png");
    private static final ResourceLocation[] SPLAT = {
            new ResourceLocation(DBZenith.MOD_ID, "textures/entity/blood_splat_1.png"),
            new ResourceLocation(DBZenith.MOD_ID, "textures/entity/blood_splat_2.png"),
            new ResourceLocation(DBZenith.MOD_ID, "textures/entity/blood_splat_3.png")};

    private static final int DROPS = 700, SPLATS = 300, SPLAT_LIFE = 1200;
    private static final float[] dx = new float[DROPS], dy = new float[DROPS], dz = new float[DROPS],
            vx = new float[DROPS], vy = new float[DROPS], vz = new float[DROPS], ds = new float[DROPS],
            pdx = new float[DROPS], pdy = new float[DROPS], pdz = new float[DROPS];
    private static final int[] dc = new int[DROPS], dage = new int[DROPS], dstyle = new int[DROPS];
    private static int drops, dropNext;

    private static final float[] sx = new float[SPLATS], sy = new float[SPLATS], sz = new float[SPLATS], ss = new float[SPLATS], srot = new float[SPLATS];
    private static final int[] sc = new int[SPLATS], sborn = new int[SPLATS], stex = new int[SPLATS];
    private static int splats, splatNext, ticks;

    private BloodFx() {}

    // ------------------------------------------------------------------ colours, on the client

    /** This fighter's blood colour, as seen from here (a player's race from their public state). */
    public static int color(LivingEntity e) {
        if (e instanceof Player) {
            var st = com.dbzenith.client.ClientPublicStates.get(e.getId());
            return Blood.color(st == null ? null : st.raceEnum());
        }
        return Blood.color(Blood.race(e));
    }

    public static int style(LivingEntity e) {
        if (e instanceof Player) {
            var st = com.dbzenith.client.ClientPublicStates.get(e.getId());
            return Blood.style(st == null ? null : st.raceEnum());
        }
        return Blood.style(Blood.race(e));
    }

    // ------------------------------------------------------------------ spilling

    /** A spill from the server ({@link ImpactPacket#BLOOD}). */
    public static void onImpact(ImpactPacket m) {
        int style = m.flags() >> 4 & 15;
        boolean spurt = (m.flags() & 1) != 0;
        Vec3 at = new Vec3(m.x(), m.y(), m.z()), dir = new Vec3(m.dx(), m.dy(), m.dz());
        int n = Math.max(1, Math.round(m.scale() * quality()));
        RandomSource r = rnd();
        for (int i = 0; i < n; i++) {
            Vec3 v;
            if (spurt) v = dir.scale(0.18 + r.nextDouble() * 0.22).add(r.nextGaussian() * 0.05, r.nextGaussian() * 0.05, r.nextGaussian() * 0.05);
            else v = new Vec3(r.nextGaussian() * 0.12, 0.05 + r.nextDouble() * 0.18, r.nextGaussian() * 0.12);
            add(at.add(r.nextGaussian() * 0.15, r.nextGaussian() * 0.15, r.nextGaussian() * 0.15), v, m.color(), style, 0.06f + r.nextFloat() * 0.06f);
        }
        if (style == Blood.OIL || style == Blood.SPARKS) KiParticles.burst(at, 0xFFE8A0, 6 + n / 4, 0.25f, 0.35f);
    }

    /** One drop. */
    public static void add(Vec3 at, Vec3 v, int color, int style, float size) {
        int i;
        if (drops < DROPS) i = drops++;
        else {
            i = dropNext;
            dropNext = (dropNext + 1) % DROPS;
        }
        dx[i] = pdx[i] = (float) at.x;
        dy[i] = pdy[i] = (float) at.y;
        dz[i] = pdz[i] = (float) at.z;
        vx[i] = (float) v.x;
        vy[i] = (float) v.y;
        vz[i] = (float) v.z;
        ds[i] = style == Blood.GOO ? size * 2.2f : size;
        dc[i] = color;
        dstyle[i] = style;
        dage[i] = 0;
    }

    /** A cut-off limb lying in its blood: a drop from its end now and then. */
    public static void drip(com.dbzenith.combat.SeveredLimb limb) {
        RandomSource r = rnd();
        add(limb.position().add(r.nextGaussian() * 0.08, 0.1, r.nextGaussian() * 0.08), new Vec3(r.nextGaussian() * 0.02, 0.02, r.nextGaussian() * 0.02),
                limb.blood(), Blood.PLAIN, 0.05f);
    }

    private static void splat(double x, double y, double z, float size, int color) {
        int i;
        if (splats < SPLATS) i = splats++;
        else {
            i = splatNext;
            splatNext = (splatNext + 1) % SPLATS;
        }
        RandomSource r = rnd();
        sx[i] = (float) x;
        sy[i] = (float) y;
        sz[i] = (float) z;
        ss[i] = size;
        srot[i] = r.nextFloat() * Mth.TWO_PI;
        sc[i] = color;
        sborn[i] = ticks;
        stex[i] = r.nextInt(SPLAT.length);
    }

    private static float quality() {
        return switch (KiParticles.quality()) {
            case 0 -> 0.4f;
            case 1 -> 0.7f;
            default -> 1f;
        };
    }

    private static RandomSource rnd() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? RandomSource.create() : level.random;
    }

    // ------------------------------------------------------------------ every tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            drops = splats = 0;
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        for (int i = 0; i < drops; i++) {
            pdx[i] = dx[i];
            pdy[i] = dy[i];
            pdz[i] = dz[i];
            if (dage[i] < 0) continue;
            dage[i]++;
            float grav = dstyle[i] == Blood.GOO ? 0.025f : 0.04f;
            vy[i] -= grav;
            vx[i] *= 0.98f;
            vz[i] *= 0.98f;
            float nx = dx[i] + vx[i], ny = dy[i] + vy[i], nz = dz[i] + vz[i];
            BlockPos below = BlockPos.containing(nx, ny, nz);
            BlockState bs = level.getBlockState(below);
            if (!bs.getCollisionShape(level, below).isEmpty() || dage[i] > 100) {    // it lands: a splat on the surface
                if (dage[i] <= 100 && bs.getFluidState().isEmpty()) {
                    double top = below.getY() + bs.getCollisionShape(level, below).max(net.minecraft.core.Direction.Axis.Y);
                    if (vy[i] < 0 && top <= dy[i] + 0.05)
                        splat(nx, top + 0.01, nz, ds[i] * (dstyle[i] == Blood.GOO ? 4f : 5f) * (0.7f + rnd().nextFloat() * 0.6f), dc[i]);
                }
                dage[i] = -1;
                continue;
            }
            dx[i] = nx;
            dy[i] = ny;
            dz[i] = nz;
        }
        int w = 0;                                                               // drop the dead ones
        for (int i = 0; i < drops; i++) {
            if (dage[i] < 0) continue;
            if (w != i) copyDrop(i, w);
            w++;
        }
        drops = w;
        dropNext = 0;
        if (ticks % 2 == 0) stumps(level);
    }

    private static void copyDrop(int from, int to) {
        dx[to] = dx[from]; dy[to] = dy[from]; dz[to] = dz[from];
        pdx[to] = pdx[from]; pdy[to] = pdy[from]; pdz[to] = pdz[from];
        vx[to] = vx[from]; vy[to] = vy[from]; vz[to] = vz[from];
        ds[to] = ds[from]; dc[to] = dc[from]; dage[to] = dage[from]; dstyle[to] = dstyle[from];
    }

    /** Fresh wounds drip, and pulse with the heartbeat. */
    private static void stumps(ClientLevel level) {
        RandomSource r = level.random;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for (var en : Limbs.clientStates().entrySet()) {
            Limbs.State s = en.getValue();
            if (s.bleeding == 0) continue;
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || e.position().distanceToSqr(cam) > 48 * 48) continue;
            int color = color(le), style = style(le);
            boolean beat = ticks % 16 == 0;
            for (Limbs.Limb l : Limbs.Limb.values()) {
                if (!s.bleeding(l)) continue;
                Vec3 socket = socket(le, l);
                double yr = Math.toRadians(le.yBodyRot);
                Vec3 out = new Vec3(-Math.cos(yr), 0, -Math.sin(yr)).scale(l == Limbs.Limb.RIGHT_ARM || l == Limbs.Limb.RIGHT_LEG ? 1 : -1);
                add(socket, out.scale(0.03).add(r.nextGaussian() * 0.02, -0.02, r.nextGaussian() * 0.02), color, style, 0.05f + r.nextFloat() * 0.03f);
                if (beat) for (int k = 0; k < 4; k++)                           // the heartbeat pushes a spurt
                    add(socket, out.scale(0.12 + r.nextDouble() * 0.08).add(r.nextGaussian() * 0.03, 0.06 + r.nextDouble() * 0.05, r.nextGaussian() * 0.03),
                            color, style, 0.05f + r.nextFloat() * 0.04f);
                if ((style == Blood.OIL || style == Blood.SPARKS) && r.nextInt(6) == 0)
                    KiParticles.add(KiParticles.SPARK, socket.x, socket.y, socket.z, out.x * 0.15, 0.08, out.z * 0.15, 0.3f, 0xFFE8A0, 5, 0.85f, 0.03f);
            }
        }
    }

    /** Where a limb meets the body. */
    public static Vec3 socket(LivingEntity e, Limbs.Limb l) {
        double yr = Math.toRadians(e.yBodyRot);
        Vec3 right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
        float h = e.getBbHeight(), s = h / 1.8f;
        boolean r = l == Limbs.Limb.RIGHT_ARM || l == Limbs.Limb.RIGHT_LEG;
        double side = (l.arm() ? 0.36 : 0.12) * s * (r ? 1 : -1);
        double up = l.arm() ? h * 0.78 : h * 0.42;
        if (com.dbzenith.client.ClientKnockouts.isDown(e.getId())) up = 0.2;   // lying down
        return e.position().add(right.scale(side)).add(0, up, 0);
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || drops == 0 && splats == 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        Quaternionf rot = event.getCamera().rotation();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (int t = 0; t < SPLAT.length; t++) {                                // splats on the ground, drying and fading
            RenderType type = RenderType.entityTranslucent(SPLAT[t]);
            VertexConsumer vc = buffers.getBuffer(type);
            Matrix4f m = pose.last().pose();
            for (int i = 0; i < splats; i++) {
                if (stex[i] != t) continue;
                int age = ticks - sborn[i];
                if (age > SPLAT_LIFE) continue;
                float fade = age < SPLAT_LIFE - 200 ? 1 : (SPLAT_LIFE - age) / 200f;
                float dry = Math.min(1, age / 600f);                            // it darkens as it dries
                int c = FxDraw.mix(sc[i], 0x000000, 0.35f * dry);
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(sx[i], sy[i] + 0.1, sz[i]));
                float x = (float) (sx[i] - cam.x), y = (float) (sy[i] - cam.y), z = (float) (sz[i] - cam.z), h = ss[i] / 2;
                float cs = Mth.cos(srot[i]) * h, sn = Mth.sin(srot[i]) * h;
                quad(vc, m, x - cs + sn, y, z - sn - cs, x + cs + sn, y, z + sn - cs, x + cs - sn, y, z + sn + cs, x - cs - sn, y, z - sn + cs,
                        c, (int) (225 * fade), light);
            }
            buffers.endBatch(type);
        }
        RenderType dropType = RenderType.entityTranslucent(DROP);                // drops in flight
        VertexConsumer vc = buffers.getBuffer(dropType);
        for (int i = 0; i < drops; i++) {
            float x = (float) (Mth.lerp(pt, pdx[i], dx[i]) - cam.x), y = (float) (Mth.lerp(pt, pdy[i], dy[i]) - cam.y), z = (float) (Mth.lerp(pt, pdz[i], dz[i]) - cam.z);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(dx[i], dy[i], dz[i]));
            FxDraw.billboard(pose, vc, rot, x, y, z, ds[i], 0, 0, 0, 1, 1, dc[i], 235, light);
        }
        buffers.endBatch(dropType);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, int rgb, int a, int light) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        vc.vertex(m, x0, y0, z0).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
    }
}
