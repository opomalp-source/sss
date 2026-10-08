package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.fx.FxDraw;
import com.dbzenith.client.fx.FxRenderTypes;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * The aura, v3 (CX-11a). A layered flame shell wrapped round the body like a teardrop (narrow at the feet, widest at the
 * chest, its tongues bending in to meet above the head): a saturated outer body, a white-hot additive core and sharp
 * licks that shoot off the top and fade. Every tongue is a strip bent along that shape, swaying as a wave runs up it,
 * with an animated flame texture whose light flows upward. Each family of forms burns its own way ({@link Style}):
 * spiky gold flame, roaring rage, calm god ki, Blue's sparkle, silver wisps, a dark evil burn. Starting to charge
 * bursts out in a shockwave and flash; charging on the ground kicks up dust and lifts rocks (see {@link AuraDebris}).
 */
// Retired by the aura system (CX-24): every form, the base form's charge and Kaioken now have aura files, so this is
// no longer subscribed. Kept for reference until the new auras have been played with for a while.
public final class AuraRenderer {
    private static final ResourceLocation TONGUE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/aura_tongue.png");
    private static final ResourceLocation SPIKE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/aura_spike.png");
    private static final ResourceLocation WISP = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/aura_wisp.png");
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final ResourceLocation RING = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/shock_ring.png");
    private static final ResourceLocation STAR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/impact_star.png");
    private static final ResourceLocation STREAK = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/fx_streak.png");
    private static final int FRAMES = 16;

    /** How a family of forms burns. */
    public enum Style {
        //            tongues radius height speed outer core licks wobble
        FLAME(16, 0.6f, 1.0f, 1.0f, 175, 150, 0.35f, 0.14f),
        ROAR(20, 0.68f, 1.15f, 1.5f, 190, 170, 0.7f, 0.2f),
        CALM(12, 0.5f, 0.9f, 0.45f, 150, 175, 0f, 0.05f),
        SPARKLE(12, 0.52f, 0.92f, 0.5f, 140, 185, 0f, 0.06f),
        WISPS(10, 0.48f, 0.85f, 0.4f, 90, 150, 0f, 0.05f),
        DARK(18, 0.62f, 1.05f, 0.8f, 200, 110, 0.3f, 0.16f);

        final int tongues;
        final float radius, height, speed, licks, wobble;
        final int outer, core;

        Style(int tongues, float radius, float height, float speed, int outer, int core, float licks, float wobble) {
            this.tongues = tongues;
            this.radius = radius;
            this.height = height;
            this.speed = speed;
            this.outer = outer;
            this.core = core;
            this.licks = licks;
            this.wobble = wobble;
        }
    }

    /** Which way a form burns. */
    public static Style styleOf(Form form, boolean kaioken, int color) {
        if (kaioken) return Style.ROAR;
        String id = form.id();
        if (form.calmAura()) {
            if (id.contains("blue")) return Style.SPARKLE;
            int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
            boolean pale = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 50 && r > 150;
            if (pale || id.contains("ego") || id.contains("eye") || id.contains("transcendent")) return Style.WISPS;
            return Style.CALM;
        }
        for (String k : new String[]{"lssj", "legendary", "berserk", "wrath", "rage", "beast", "overflow", "primordial"}) {
            if (id.contains(k)) return Style.ROAR;
        }
        for (String k : new String[]{"evil", "dark", "corruption", "demon", "nightborn", "blood", "crimson", "thirst"}) {
            if (id.contains(k)) return Style.DARK;
        }
        return Style.FLAME;
    }

    /** When each player started charging (client ticks), for the burst. */
    private static final Map<Integer, Float> CHARGE_START = new HashMap<>();
    private static final Map<Integer, Boolean> WAS_POWERING = new HashMap<>();

    private AuraRenderer() {}

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (com.dbzenith.client.ui.PortraitRenderer.isDrawing()) return;
        Player player = event.getEntity();
        if (player.isInvisible() || player.isSpectator()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        if (com.dbzenith.client.aura.AuraSystem.handles(player, state)) return;   // drawn by the aura system (CX-24)
        float partial = event.getPartialTick();
        float t = player.tickCount + partial;
        boolean powering = state.powering();
        boolean was = WAS_POWERING.getOrDefault(player.getId(), false);
        if (powering && !was) CHARGE_START.put(player.getId(), t);
        WAS_POWERING.put(player.getId(), powering);
        if (WAS_POWERING.size() > 256) {
            WAS_POWERING.clear();
            CHARGE_START.clear();
        }

        Form form = Forms.byId(state.form());
        boolean kaioken = state.has(PublicStatePacket.KAIOKEN);
        boolean held = !form.isBase() || kaioken;
        Float started = CHARGE_START.get(player.getId());
        boolean bursting = started != null && t - started < 12;
        if (!powering && !held && !bursting) return;
        if (form == Forms.GREAT_APE) return;                                   // the ape has no ki aura, only its size

        int color = state.auraColor();
        Style style = styleOf(form, kaioken, color);
        float release = 0.75f + 0.25f * Mth.clamp(state.release() / 100f, 0f, 1.5f);
        float tier = 1f + 0.07f * Math.min(5, form.tier());
        float height = (powering ? 2.55f : 2.1f) * release * tier * style.height;
        float strength = powering || held ? (powering ? 1f : 0.75f) : 0f;
        int core = FxDraw.mix(color, 0xFFFFFF, 0.6f);
        int outerColor = style == Style.DARK ? FxDraw.mix(color, 0x0A0010, 0.86f) : color;

        Minecraft mc = Minecraft.getInstance();
        Vec3 feet = new Vec3(Mth.lerp(partial, player.xo, player.getX()), Mth.lerp(partial, player.yo, player.getY()),
                Mth.lerp(partial, player.zo, player.getZ()));
        float scale = com.dbzenith.transform.GreatApe.scaleOf(player);
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition().subtract(feet).scale(1 / scale);

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        long seed = player.getId() * 341873128712L;
        int detail = com.dbzenith.config.DBZConfig.CLIENT.auraDetail.get();  // 0 low, 1 normal, 2 high
        float more = detail == 0 ? 0.6f : detail == 2 ? 1.35f : 1f;

        if (bursting) burst(pose, buffers, t - started, color, core, player.onGround());
        if (strength <= 0) return;

        // the halo behind everything
        VertexConsumer halo = buffers.getBuffer(FxRenderTypes.additive(GLOW));
        FxDraw.billboard(pose, halo, mc.getEntityRenderDispatcher().cameraOrientation(), 0, 1.0, 0,
                (style == Style.CALM || style == Style.WISPS ? 3.0f : 3.7f) * release, 0, 0, 0, 1, 1,
                style == Style.DARK ? FxDraw.mix(color, 0x000000, 0.3f) : color, (int) ((style == Style.DARK ? 70 : 115) * strength));
        if (powering && player.onGround()) {                                    // the ground lit under a charge
            VertexConsumer ring = buffers.getBuffer(FxRenderTypes.additive(RING));
            FxDraw.plane(pose, ring, new Vec3(0, 0.04, 0), new Vec3(0, 1, 0), 1.2f + 0.1f * Mth.sin(t * 0.7f), t * 0.05f,
                    color, 110, LightTexture.FULL_BRIGHT);
        }

        float speed = style.speed * (powering ? 1.35f : 1f);
        int n = Math.round(style.tongues * more);
        // the outer body: ordinary blending keeps the colour saturated in daylight
        VertexConsumer outer = buffers.getBuffer(FxRenderTypes.soft(TONGUE));
        shell(pose, outer, n, style.radius, height, -0.15f, 0.62f, t, speed, seed, eye, outerColor, (int) (style.outer * strength), style.wobble);
        // the white-hot core
        VertexConsumer inner = buffers.getBuffer(FxRenderTypes.additive(TONGUE));
        int coreColor = style == Style.DARK ? FxDraw.mix(color, 0x000000, 0.45f) : core;
        shell(pose, inner, Math.max(6, n * 2 / 3), style.radius * 0.62f, height * 0.84f, -0.08f, 0.5f, t, speed * 1.2f, seed + 7, eye,
                coreColor, (int) (style.core * strength), style.wobble * 0.7f);
        if (style == Style.DARK) {                                              // a lit rim round the dark flame
            shell(pose, inner, n, style.radius * 1.06f, height * 1.02f, -0.15f, 0.3f, t, speed, seed + 3, eye, color, (int) (45 * strength), style.wobble);
        }

        // licks: sharp tongues shooting off the top and fading
        float lickRate = style.licks * (powering ? 1.6f : 1f) * (detail == 0 ? 0.5f : 1f);
        if (lickRate > 0) licks(pose, buffers.getBuffer(FxRenderTypes.additive(SPIKE)), player.getId(), t, lickRate, height, style.radius, eye, core);
        if (style == Style.SPARKLE) sparkles(pose, buffers, player.getId(), t, height, mc, FxDraw.mix(color, 0xFFFFFF, 0.5f));
        if (style == Style.WISPS) wisps(pose, buffers.getBuffer(FxRenderTypes.additive(WISP)), player.getId(), t, height, eye, core);
        if (style == Style.DARK && detail > 0) wisps(pose, buffers.getBuffer(FxRenderTypes.soft(WISP)), player.getId() + 99, t, height, eye,
                FxDraw.mix(color, 0x000000, 0.8f));
        if (form.lightning()) lightning(pose, buffers, player, t, eye, color);
    }

    // ------------------------------------------------------------------ the shell

    /** A point on the teardrop at height fraction s (0 feet .. 1 tip), angle a, before sway. */
    private static Vec3 onShape(float radius, float height, float baseY, float s, float a) {
        float r = radius * (0.62f + 0.42f * Mth.sin(Mth.PI * Math.min(1, s * 1.05f))) * (1 - 0.92f * s * s * s * s);
        return new Vec3(Mth.cos(a) * r, baseY + s * height, Mth.sin(a) * r);
    }

    /**
     * {@code n} tongues bent along the teardrop, each a strip of four segments turned to face the camera about its own
     * length, swaying as a wave runs up it, flickering in height, its texture frame advancing with time.
     */
    private static void shell(PoseStack pose, VertexConsumer vc, int n, float radius, float height, float baseY, float width,
                              float t, float speed, long seed, Vec3 eye, int color, int alpha, float wobble) {
        if (alpha <= 0) return;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int light = LightTexture.FULL_BRIGHT;
        int segs = 4;
        Vec3[] pts = new Vec3[segs + 1];
        for (int i = 0; i < n; i++) {
            float phase = (float) (((seed >>> (i % 32)) & 1023) / 1023.0 * Mth.TWO_PI) + i * 2.1f;
            float a = i * Mth.TWO_PI / n + t * 0.012f;
            float h = height * (1 + wobble * Mth.sin(t * 0.37f + phase) + 0.5f * wobble * Mth.sin(t * 0.91f + phase * 1.7f));
            Vec3 tangent = new Vec3(-Mth.sin(a), 0, Mth.cos(a));
            for (int k = 0; k <= segs; k++) {
                float s = k / (float) segs;
                float sway = 0.09f * s * Mth.sin(t * 0.3f * speed + phase - s * 3.5f);   // a wave running up the tongue
                pts[k] = onShape(radius, h, baseY, s, a).add(tangent.scale(sway));
            }
            int frame = Math.floorMod((int) (t * speed * 0.9f + phase * 3), FRAMES);
            float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES;
            for (int k = 0; k < segs; k++) {
                Vec3 p0 = pts[k], p1 = pts[k + 1];
                Vec3 along = p1.subtract(p0);
                Vec3 side0 = facing(along, p0, eye).scale(width / 2), side1 = facing(along, p1, eye).scale(width / 2);
                float v0 = 1 - k / (float) segs, v1 = 1 - (k + 1) / (float) segs;
                corner(vc, m, nm, p0.subtract(side0), u0, v0, color, alpha, light);
                corner(vc, m, nm, p0.add(side0), u1, v0, color, alpha, light);
                corner(vc, m, nm, p1.add(side1), u1, v1, color, alpha, light);
                corner(vc, m, nm, p1.subtract(side1), u0, v1, color, alpha, light);
            }
        }
    }

    /** The sideways direction of a strip running along {@code along} at {@code p}, turned to face the camera. */
    private static Vec3 facing(Vec3 along, Vec3 p, Vec3 eye) {
        Vec3 side = along.cross(eye.subtract(p));
        return side.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : side.normalize();
    }

    // ------------------------------------------------------------------ extras

    /** Sharp licks: each slot fires now and then, shooting up from the shell's shoulder and fading over a few ticks. */
    private static void licks(PoseStack pose, VertexConsumer vc, int id, float t, float rate, float height, float radius, Vec3 eye, int color) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int slots = 8;
        for (int i = 0; i < slots; i++) {
            float period = 9 + (i * 7 % 5);
            int cycle = (int) Math.floor((t + i * 3.3f) / period);
            float life = ((t + i * 3.3f) % period) / 7f;                         // a lick lasts seven ticks of each period
            if (life > 1) continue;
            RandomSource rnd = RandomSource.create(id * 7919L + i * 131L + cycle * 31L);
            if (rnd.nextFloat() > rate) continue;
            float a = rnd.nextFloat() * Mth.TWO_PI, s0 = 0.55f + 0.3f * rnd.nextFloat();
            Vec3 base = onShape(radius * 1.05f, height, -0.1f, s0, a);
            Vec3 dir = new Vec3(base.x * 0.4, 1, base.z * 0.4).normalize();
            float len = (0.5f + 0.5f * rnd.nextFloat()) * height * 0.45f * (0.4f + 0.6f * life);
            Vec3 tip = base.add(dir.scale(len)).add(0, life * 0.4f, 0);
            Vec3 side = facing(tip.subtract(base), base, eye).scale(0.12f);
            int alpha = (int) (220 * (1 - life));
            corner(vc, m, nm, base.subtract(side), 0, 1, color, alpha, LightTexture.FULL_BRIGHT);
            corner(vc, m, nm, base.add(side), 1, 1, color, alpha, LightTexture.FULL_BRIGHT);
            corner(vc, m, nm, tip.add(side), 1, 0, color, alpha, LightTexture.FULL_BRIGHT);
            corner(vc, m, nm, tip.subtract(side), 0, 0, color, alpha, LightTexture.FULL_BRIGHT);
        }
    }

    /** Blue's sparkle: points of light rising through the aura and winking out. */
    private static void sparkles(PoseStack pose, MultiBufferSource buffers, int id, float t, float height, Minecraft mc, int color) {
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.additive(STAR));
        for (int i = 0; i < 14; i++) {
            float period = 24 + i % 7;
            int cycle = (int) Math.floor((t + i * 5.1f) / period);
            float life = ((t + i * 5.1f) % period) / period;
            RandomSource rnd = RandomSource.create(id * 3571L + i * 97L + cycle * 17L);
            float a = rnd.nextFloat() * Mth.TWO_PI, r = 0.25f + 0.4f * rnd.nextFloat();
            float y = life * height * 1.05f;
            float size = 0.16f * Mth.sin(Mth.PI * life) * (0.7f + 0.6f * rnd.nextFloat());
            FxDraw.billboard(pose, vc, mc.getEntityRenderDispatcher().cameraOrientation(), Mth.cos(a) * r, y, Mth.sin(a) * r, size,
                    t * 0.1f + i, 0, 0, 1, 1, color, (int) (230 * Mth.sin(Mth.PI * life)));
        }
    }

    /** Wisps: soft curls drifting up and spiralling round the body. */
    private static void wisps(PoseStack pose, VertexConsumer vc, int id, float t, float height, Vec3 eye, int color) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        for (int i = 0; i < 7; i++) {
            float period = 30 + i * 3;
            float life = ((t + i * 6.7f) % period) / period;
            float a = i * 0.9f + t * 0.04f + life * 1.6f;
            float r = 0.45f + 0.15f * Mth.sin(i * 1.7f);
            Vec3 base = new Vec3(Mth.cos(a) * r, life * height * 0.9f, Mth.sin(a) * r);
            Vec3 tip = base.add(-Mth.sin(a) * 0.15f, 0.55f, Mth.cos(a) * 0.15f);
            Vec3 side = facing(tip.subtract(base), base, eye).scale(0.18f);
            int alpha = (int) (170 * Mth.sin(Mth.PI * life));
            corner(vc, m, nm, base.subtract(side), 0, 1, color, alpha, LightTexture.FULL_BRIGHT);
            corner(vc, m, nm, base.add(side), 1, 1, color, alpha, LightTexture.FULL_BRIGHT);
            corner(vc, m, nm, tip.add(side), 1, 0, color, alpha, LightTexture.FULL_BRIGHT);
            corner(vc, m, nm, tip.subtract(side), 0, 0, color, alpha, LightTexture.FULL_BRIGHT);
        }
    }

    /** The burst when a charge starts: a flash, a shockwave racing out along the ground and a ring flung up round the body. */
    private static void burst(PoseStack pose, MultiBufferSource buffers, float age, int color, int core, boolean ground) {
        float p = age / 12f, fade = 1 - p;
        Minecraft mc = Minecraft.getInstance();
        VertexConsumer flash = buffers.getBuffer(FxRenderTypes.additive(GLOW));
        FxDraw.billboard(pose, flash, mc.getEntityRenderDispatcher().cameraOrientation(), 0, 1.0, 0, 2.5f + 3.5f * p, 0, 0, 0, 1, 1,
                core, (int) (220 * fade * fade));
        VertexConsumer ring = buffers.getBuffer(FxRenderTypes.additive(RING));
        if (ground) FxDraw.plane(pose, ring, new Vec3(0, 0.06, 0), new Vec3(0, 1, 0), 0.8f + 5.5f * p, 0, color, (int) (230 * fade), LightTexture.FULL_BRIGHT);
        FxDraw.plane(pose, ring, new Vec3(0, 1.0, 0), new Vec3(0, 1, 0), 0.6f + 3.2f * p, 0, core, (int) (180 * fade), LightTexture.FULL_BRIGHT);
    }

    /** Two or three jagged bolts crawling over the body, re-struck every other tick. */
    private static void lightning(PoseStack pose, MultiBufferSource buffers, Player player, float t, Vec3 eye, int color) {
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.additive(STREAK));
        int glow = FxDraw.mix(color, 0xBFE6FF, 0.5f);
        int strike = (int) t / 2;
        for (int k = 0; k < 3; k++) {
            RandomSource rnd = RandomSource.create(player.getId() * 7919L + strike * 131L + k * 31L);
            if (rnd.nextFloat() > 0.6f) continue;
            double a = rnd.nextDouble() * Math.PI * 2;
            Vec3 p = new Vec3(Math.cos(a) * 0.38, 0.3 + rnd.nextDouble() * 1.4, Math.sin(a) * 0.38);
            for (int s = 0; s < 6; s++) {
                a += (rnd.nextDouble() - 0.5) * 1.2;
                Vec3 next = new Vec3(Math.cos(a) * (0.3 + rnd.nextDouble() * 0.2), p.y + (rnd.nextDouble() - 0.45) * 0.4,
                        Math.sin(a) * (0.3 + rnd.nextDouble() * 0.2));
                FxDraw.ribbon(pose, vc, p, next, eye, 0.2f, glow, 140);
                FxDraw.ribbon(pose, vc, p, next, eye, 0.06f, 0xFFFFFF, 255);
                p = next;
            }
        }
        for (int k = 0; k < 2; k++) {                                          // now and then a bolt arcs down into the ground
            RandomSource rnd = RandomSource.create(player.getId() * 104729L + strike * 7L + k * 13L);
            if (rnd.nextFloat() > 0.3f || !player.onGround()) continue;
            double a = rnd.nextDouble() * Math.PI * 2, reach = 1.2 + rnd.nextDouble() * 1.4;
            Vec3 from = new Vec3(Math.cos(a) * 0.35, 0.8 + rnd.nextDouble() * 0.7, Math.sin(a) * 0.35);
            Vec3 to = new Vec3(Math.cos(a) * reach, 0.03, Math.sin(a) * reach), p = from;
            for (int s = 1; s <= 6; s++) {
                Vec3 next = from.lerp(to, s / 6.0).add(s < 6 ? (rnd.nextDouble() - 0.5) * 0.35 : 0, s < 6 ? (rnd.nextDouble() - 0.5) * 0.2 : 0,
                        s < 6 ? (rnd.nextDouble() - 0.5) * 0.35 : 0);
                FxDraw.ribbon(pose, vc, p, next, eye, 0.22f, glow, 150);
                FxDraw.ribbon(pose, vc, p, next, eye, 0.07f, 0xFFFFFF, 255);
                p = next;
            }
        }
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int rgb, int alpha, int light) {
        FxDraw.vertex(vc, m, n, (float) p.x, (float) p.y, (float) p.z, u, v, rgb, alpha, light);
    }
}
