package com.dbzenith.nimbus;

import com.dbzenith.client.fx.FxDraw;
import com.dbzenith.client.fx.FxRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Kinto'un, the Flying Nimbus (CX-39): a living golden cloud rather than a solid shape. About thirty soft puffs, each
 * breathing and churning on its own, bright yellow on top and warm orange underneath with a few white-gold highlights,
 * gathered into a fat body with a curling tail. The whole cloud tilts nose-down as it speeds up and back as it brakes,
 * banks into turns and stretches out with speed, the tail streaming back. Behind it: a glowing golden streak, puffs
 * peeling off the tail and swelling as they fade, and glints in the wake.
 */
public class NimbusRenderer extends EntityRenderer<NimbusEntity> {
    private static final ResourceLocation PUFF = new ResourceLocation("dbzenith", "textures/entity/nimbus_puff.png");
    private static final ResourceLocation GLOW = new ResourceLocation("dbzenith", "textures/entity/ki_glow.png");
    private static final ResourceLocation STAR = new ResourceLocation("dbzenith", "textures/entity/impact_star.png");
    /** The cloud's colours: its lit top, its middle, its shaded underside, and the highlights. */
    private static final int TOP = 0xFFF49A, MID = 0xFFE05A, UNDER = 0xFFC24E, SHINE = 0xFFFDEA;

    /** One puff of the body: where it sits (x across, y up, z forward), how big, and whether it is a highlight. */
    private record Spot(float x, float y, float z, float size, boolean shine) {}
    private static final Spot[] BODY = body();
    private record Draw(Vec3 at, float size, int rgb, int alpha, float spin, double depth) {}

    public NimbusRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = .9f;
    }

    @Override
    public ResourceLocation getTextureLocation(NimbusEntity e) {
        return PUFF;
    }

    @Override
    public boolean shouldRender(NimbusEntity e, Frustum f, double x, double y, double z) {
        return f.isVisible(e.getBoundingBox().inflate(30));
    }

    private static Spot[] body() {
        List<Spot> s = new ArrayList<>();
        s.add(new Spot(0, .42f, .05f, 1.25f, false));                                   // the heart
        for (int i = 0; i < 10; i++) {                                                  // the round body
            double a = i * Math.PI * 2 / 10 + .2;
            s.add(new Spot((float) (Math.sin(a) * .78), .3f + (float) Math.cos(a * 3) * .04f, (float) (Math.cos(a) * .82), .8f, false));
        }
        for (int i = 0; i < 6; i++) {                                                   // the billowing top
            double a = i * Math.PI * 2 / 6 + .5;
            s.add(new Spot((float) (Math.sin(a) * .42), .62f, (float) (Math.cos(a) * .45 + .05), .78f, false));
        }
        s.add(new Spot(0, .78f, .15f, .7f, false));
        for (int i = 0; i < 4; i++) {                                                   // the underside
            double a = i * Math.PI / 2 + .8;
            s.add(new Spot((float) (Math.sin(a) * .45), .12f, (float) (Math.cos(a) * .45), .7f, false));
        }
        s.add(new Spot(0, .45f, -1.0f, .72f, false));                                  // the tail, curling up at its tip
        s.add(new Spot(.06f, .52f, -1.38f, .55f, false));
        s.add(new Spot(.1f, .64f, -1.68f, .38f, false));
        s.add(new Spot(.06f, .8f, -1.82f, .24f, false));
        s.add(new Spot(-.25f, .78f, .35f, .34f, true));                                 // highlights on the top
        s.add(new Spot(.3f, .72f, -.2f, .28f, true));
        s.add(new Spot(.05f, .92f, .05f, .3f, true));
        return s.toArray(new Spot[0]);
    }

    @Override
    public void render(NimbusEntity n, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        float t = n.tickCount + partial;
        float tilt = Mth.lerp(partial, n.tiltO, n.tilt), bank = Mth.lerp(partial, n.bankO, n.bank), stretch = Mth.lerp(partial, n.stretchO, n.stretch);
        Vec3 origin = new Vec3(Mth.lerp(partial, n.xo, n.getX()), Mth.lerp(partial, n.yo, n.getY()), Mth.lerp(partial, n.zo, n.getZ()));
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition().subtract(origin);
        float day = mc.level == null ? 1 : (mc.level.getSkyDarken(partial) - .2f) / .8f;               // 0 at night, 1 by day
        float bright = .74f + .26f * Math.max(day, LightTexture.block(light) / 15f);        // it glows, a little softer at night
        float bob = Mth.sin(t * .09f) * .05f * (1 - Math.min(1, n.speed * 3));
        // sent away: every puff drifts out from the middle, swells, turns white-gold and fades
        float gone = n.leaving() ? Mth.clamp((t - n.stateStart) / NimbusEntity.LEAVE_TICKS, 0, 1) : 0;
        float spread = 1 - (1 - gone) * (1 - gone);

        // the body: placed, turned, tilted, banked and stretched, then sorted back to front
        Quaternionf turn = new Quaternionf().rotationY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(tilt)).rotateZ((float) Math.toRadians(bank));
        List<Draw> draws = new ArrayList<>(BODY.length + n.puffs.size());
        for (int i = 0; i < BODY.length; i++) {
            Spot s = BODY[i];
            float ph = i * 1.93f;
            float breathe = 1 + .07f * Mth.sin(t * .075f + ph);
            float churn = .045f * (1 + n.speed * 2);
            float z = s.z() * (1 + stretch * (s.z() < -.5f ? 1.4f : .35f));             // the tail streams back the most
            Vector3f p = new Vector3f(s.x() * (1 + spread * 1.6f) + Mth.sin(t * .05f + ph) * churn, s.y() * (1 - stretch * .18f) + (s.y() - .25f) * spread * 1.2f + spread * .5f + Mth.cos(t * .06f + ph) * churn * .7f, z * (1 + spread * 1.6f) + Mth.sin(t * .04f + ph * 1.3f) * churn);
            turn.transform(p);
            float shade = Mth.clamp((s.y() - .1f) / .7f, 0, 1);
            int rgb = s.shine() ? SHINE : shade > .5f ? FxDraw.mix(MID, TOP, (shade - .5f) * 2) : FxDraw.mix(UNDER, MID, shade * 2);
            Vec3 at = new Vec3(p.x, p.y + bob, p.z);
            if (gone > 0) rgb = FxDraw.mix(rgb, SHINE, gone);
            int alpha = (int) ((s.shine() ? 200 : 245) * Math.pow(1 - gone, 1.5));
            draws.add(new Draw(at, s.size() * breathe * 1.18f * (1 + gone * .7f), dim(rgb, bright), alpha, ph + gone * 2, at.distanceToSqr(eye)));
        }
        // the puffs it has shed: golden wisps peeling off the tail, swelling as they fade (sorted in with the body)
        for (NimbusEntity.Puff p : n.puffs) {
            if (p.glint()) continue;
            float age = (t - p.born()) / p.life();
            if (age < 0 || age > 1) continue;
            Vec3 at = p.position().add(p.drift().scale(t - p.born())).subtract(origin);
            int alpha = (int) (200 * Math.pow(1 - age, 1.4));
            draws.add(new Draw(at, p.size() * (.8f + age * 1.1f), dim(FxDraw.mix(MID, TOP, age), bright), alpha, p.born() * .7f, at.distanceToSqr(eye)));
        }
        draws.sort((a, b) -> Double.compare(b.depth(), a.depth()));
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(camera.getLeftVector()), up = new Vector3f(camera.getUpVector());
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.unlit(PUFF));
        for (Draw d : draws) puff(pose, vc, left, up, d.at(), d.size(), d.spin(), d.rgb(), d.alpha());

        // the golden streak along the wake, and glints in it
        List<Vec3> pts = new ArrayList<>();
        List<Float> lives = new ArrayList<>();
        for (NimbusEntity.Wake w : n.wake) {
            float life = Math.max(0, 1 - (t - w.born()) / 34f);
            if (life > 0) { pts.add(w.position().subtract(origin)); lives.add(life); }
        }
        if (pts.size() > 1 && n.speed > .04f) {
            pts.add(n.rearPoint().subtract(origin));                                    // join the streak to the tail now
            lives.add(1f);
            float strength = Math.min(1, n.speed * 2.2f) * (1 - gone);
            // one buffer at a time: asking for the next ends the last (they share one builder)
            VertexConsumer band = buffers.getBuffer(FxRenderTypes.unlit(GLOW));
            for (int i = 0; i < pts.size() - 1; i++) {
                float l = lives.get(i);
                streak(pose, band, pts.get(i), pts.get(i + 1), eye, .62f * l, dim(MID, bright), (int) (220 * l * strength));   // gold, seen by day
            }
            VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(GLOW));
            for (int i = 0; i < pts.size() - 1; i++) {
                float l = lives.get(i);
                streak(pose, glow, pts.get(i), pts.get(i + 1), eye, .9f * l, dim(TOP, bright), (int) (90 * l * strength));
                streak(pose, glow, pts.get(i), pts.get(i + 1), eye, .22f * l, 0xFFFFFF, (int) (210 * l * strength));
            }
        }
        VertexConsumer stars = buffers.getBuffer(FxRenderTypes.additive(STAR));
        for (NimbusEntity.Puff p : n.puffs) {
            if (!p.glint()) continue;
            float age = (t - p.born()) / p.life();
            if (age < 0 || age > 1) continue;
            Vec3 at = p.position().add(p.drift().scale(t - p.born())).subtract(origin);
            float twinkle = Mth.sin(age * Mth.PI);
            puff(pose, stars, left, up, at, p.size() * (.6f + twinkle), t * .2f, SHINE, (int) (255 * twinkle));
        }
        super.render(n, yaw, partial, pose, buffers, light);
    }


    /** A strip from a to b facing the eye, soft across its width (the glow's middle row), continuous along its length. */
    private static void streak(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 eye, float width, int rgb, int alpha) {
        Vec3 side = b.subtract(a).cross(eye.subtract(a.add(b).scale(.5)));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width / 2);
        var m = pose.last().pose();
        var nm = pose.last().normal();
        int light = LightTexture.FULL_BRIGHT;
        FxDraw.vertex(vc, m, nm, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z), 0, .5f, rgb, alpha, light);
        FxDraw.vertex(vc, m, nm, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z), 1, .5f, rgb, alpha, light);
        FxDraw.vertex(vc, m, nm, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), 1, .5f, rgb, alpha, light);
        FxDraw.vertex(vc, m, nm, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z), 0, .5f, rgb, alpha, light);
    }

    /**
     * A square facing the camera (built from the camera's left and up, turned by {@code spin}), in the colour given
     * (drawn unlit for the body, so the cloud keeps its colour from every side).
     */
    private static void puff(PoseStack pose, VertexConsumer vc, Vector3f left, Vector3f up, Vec3 at, float size, float spin, int rgb, int alpha) {
        float c = Mth.cos(spin) * size * .5f, s = Mth.sin(spin) * size * .5f;
        float ax = left.x() * c + up.x() * s, ay = left.y() * c + up.y() * s, az = left.z() * c + up.z() * s;      // turned "left"
        float bx = up.x() * c - left.x() * s, by = up.y() * c - left.y() * s, bz = up.z() * c - left.z() * s;      // turned "up"
        var m = pose.last().pose();
        var nm = pose.last().normal();
        int light = LightTexture.FULL_BRIGHT;
        float x = (float) at.x, y = (float) at.y, z = (float) at.z;
        FxDraw.vertex(vc, m, nm, x + ax - bx, y + ay - by, z + az - bz, 0, 1, rgb, alpha, light);
        FxDraw.vertex(vc, m, nm, x - ax - bx, y - ay - by, z - az - bz, 1, 1, rgb, alpha, light);
        FxDraw.vertex(vc, m, nm, x - ax + bx, y - ay + by, z - az + bz, 1, 0, rgb, alpha, light);
        FxDraw.vertex(vc, m, nm, x + ax + bx, y + ay + by, z + az + bz, 0, 0, rgb, alpha, light);
    }
    private static int dim(int rgb, float f) {
        return ((int) (((rgb >> 16) & 255) * f) << 16) | ((int) (((rgb >> 8) & 255) * f) << 8) | (int) ((rgb & 255) * f);
    }
}
