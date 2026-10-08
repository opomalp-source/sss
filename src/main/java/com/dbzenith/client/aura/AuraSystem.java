package com.dbzenith.client.aura;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.fx.FxRenderTypes;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.PublicStatePacket;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.brigadier.arguments.StringArgumentType;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * The aura system (CX-24): one renderer for every fighter wearing a data-driven aura ({@link AuraDefs}). Each tick it
 * follows what every fighter does (charging, moving, getting hit, a new aura bursting out); each frame, after the
 * particles, it draws every aura far to near, layer by layer as its file lists them (glows, flame shells, hazes), then
 * all the particles in one go: sparkles or embers through the aura and energy motes rising while charging. Forms
 * without an aura file still burn the old way ({@code AuraRenderer}).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AuraSystem {
    private static final ResourceLocation WHITE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/aura_white.png");
    private static final ResourceLocation STAR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/impact_star.png");
    private static final ResourceLocation DOT = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final Supplier<ShaderInstance> SHELL_SHADER = () -> AuraShaders.shell, GLOW_SHADER = () -> AuraShaders.glow;
    private static final int MAX_DRAWN = 64;
    private static final float MAX_DISTANCE = 160;
    /** At most this many particles a frame, for every aura together. */
    private static final int MAX_PARTICLES = 900;

    /** What one fighter's aura is doing. */
    static final class State {
        final int id;
        final float seed;
        /** Each fighter's aura runs a little faster or slower, so two of the same never keep time. */
        final float pace;
        AuraDef def;
        float strength, strengthO;
        /** 0 calm .. 1 charging flat out. */
        float charge, chargeO;
        /** A hit or a swing: 1, dying away within a few ticks. */
        float flare, flareO;
        /** A new aura bursting out: 1, dying away. */
        float burst, burstO;
        /** The aura's own clock (seconds), running faster the wilder it is, so speeding up never jumps. */
        float phase, phaseO;
        /** Smoothed movement, blocks a tick. */
        float vx, vy, vz, vxO, vyO, vzO;
        int lastHurt;
        boolean lastSwing;
        long seen;
        // worked out while drawing, for the particles after
        float drawX, drawY, drawZ, drawRadius, drawHeight, drawBottom, drawBody, drawFade, drawCharge, drawPhase;
        boolean shellsDrawn;

        State(int id) {
            this.id = id;
            this.seed = (id * 0.6180339f) % 1f * 97f;
            this.pace = 0.9f + 0.2f * ((id * 0.7548777f) % 1f);
        }
    }

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();
    private static final AuraShell SHELL = new AuraShell();
    private static final State[] DRAW = new State[MAX_DRAWN];
    private static final LivingEntity[] DRAW_ENTITY = new LivingEntity[MAX_DRAWN];
    private static final float[] DRAW_DIST = new float[MAX_DRAWN];
    private static final Vector3f LEFT = new Vector3f(), UP = new Vector3f();

    /** /dbzaura: wear this aura yourself, whatever your form (null: off). */
    static AuraDef preview;
    /** /dbzaura state: hold your own aura in a state to look at it (idle, charge, fly, hit, burst). */
    static String devState = "idle";
    private static int devTicks;

    private AuraSystem() {}

    /** The aura a fighter's synced state asks for, or null. Kaioken still burns the old way until it has its layer. */
    public static AuraDef wanted(PublicStatePacket state) {
        if (state == null || state.has(PublicStatePacket.KAIOKEN)) return null;
        return AuraDefs.forForm(state.form());
    }

    /** Is this fighter's aura drawn here (so the old renderer leaves it alone)? */
    public static boolean handles(Player player, PublicStatePacket state) {
        if (player == Minecraft.getInstance().player && preview != null) return true;
        if (wanted(state) != null) return true;
        State s = STATES.get(player.getId());
        return s != null && s.strength > 0;
    }

    private static AuraDef wantedBy(AbstractClientPlayer p, Minecraft mc) {
        if (p.isSpectator()) return null;
        if (p == mc.player && preview != null) return preview;
        return wanted(ClientPublicStates.get(p.getId()));
    }

    // ------------------------------------------------------------------ each tick: follow what every fighter does

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        devTicks++;
        for (AbstractClientPlayer p : mc.level.players()) {
            AuraDef want = wantedBy(p, mc);
            State s = STATES.get(p.getId());
            if (s == null) {
                if (want == null) continue;
                s = new State(p.getId());
                STATES.put(p.getId(), s);
            }
            s.seen = now;
            follow(s, p, want, ClientPublicStates.get(p.getId()), p == mc.player);
        }
        ObjectIterator<State> it = STATES.values().iterator();
        while (it.hasNext()) {
            State s = it.next();
            if (now - s.seen > 2 || (s.strength <= 0 && s.strengthO <= 0)) it.remove();
        }
    }

    private static void follow(State s, LivingEntity e, AuraDef want, PublicStatePacket state, boolean self) {
        s.strengthO = s.strength;
        s.chargeO = s.charge;
        s.flareO = s.flare;
        s.burstO = s.burst;
        s.phaseO = s.phase;
        s.vxO = s.vx;
        s.vyO = s.vy;
        s.vzO = s.vz;

        if (want != null && (s.def == null || s.def == want || s.strength <= 0.02f)) {   // a new aura waits for the old to go
            if (s.def != want) {
                s.def = want;
                s.burst = 1;
            }
            s.strength = Math.min(1, s.strength + 0.14f);
        } else {
            s.strength = Math.max(0, s.strength - 0.12f);
        }

        boolean dev = self && preview != null;
        boolean charging = state != null && (state.powering() || state.has(PublicStatePacket.TRANSFORMING));
        if (dev && devState.equals("charge")) charging = true;
        float target = charging ? 1 : 0;
        s.charge += (target - s.charge) * (target > s.charge ? 0.2f : 0.09f);

        float dx = (float) (e.getX() - e.xo), dy = (float) (e.getY() - e.yo), dz = (float) (e.getZ() - e.zo);
        if (dev && devState.equals("fly")) {                                    // as if flying fast the way you look
            Vec3 look = Vec3.directionFromRotation(0, e.getYRot());
            dx = (float) look.x * 1.4f;
            dy = 0;
            dz = (float) look.z * 1.4f;
        }
        s.vx += (dx - s.vx) * 0.35f;
        s.vy += (dy - s.vy) * 0.35f;
        s.vz += (dz - s.vz) * 0.35f;

        if (e.hurtTime > s.lastHurt) s.flare = 1;                               // just hit
        s.lastHurt = e.hurtTime;
        if (e.swinging && !s.lastSwing) s.flare = Math.max(s.flare, 0.6f);      // just swung
        s.lastSwing = e.swinging;
        if (dev && devState.equals("hit") && devTicks % 20 == 0) s.flare = 1;
        if (dev && devState.equals("burst") && devTicks % 40 == 0) s.burst = 1;
        if (dev && devState.equals("hithold")) s.flare = 0.8f;
        else s.flare *= 0.72f;
        if (dev && devState.equals("bursthold")) s.burst = 0.55f;
        else s.burst = Math.max(0, s.burst - 0.06f);

        if (s.def != null) {
            float wild = 1 + s.def.react.chargeWild * s.charge + 0.8f * s.flare + s.burst;
            s.phase += 0.05f * wild * s.pace * s.def.speed;
            if (s.phase > 3600) {                                               // keep the clock small enough to stay smooth
                s.phase -= 3600;
                s.phaseO -= 3600;
            }
        }
    }

    // ------------------------------------------------------------------ each frame: draw

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || STATES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera camera = event.getCamera();
        float pt = event.getPartialTick();
        double cx = camera.getPosition().x, cy = camera.getPosition().y, cz = camera.getPosition().z;

        int n = 0;
        for (State s : STATES.values()) {
            float k = Mth.lerp(pt, s.strengthO, s.strength);
            if (k <= 0.001f || s.def == null || n >= MAX_DRAWN) continue;
            Entity e = mc.level.getEntity(s.id);
            if (!(e instanceof LivingEntity le) || e.isInvisible()) continue;
            double dx = Mth.lerp(pt, e.xo, e.getX()) - cx, dy = Mth.lerp(pt, e.yo, e.getY()) - cy, dz = Mth.lerp(pt, e.zo, e.getZ()) - cz;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > MAX_DISTANCE) continue;
            int at = n++;                                                         // far to near
            while (at > 0 && DRAW_DIST[at - 1] < dist) {
                DRAW[at] = DRAW[at - 1];
                DRAW_ENTITY[at] = DRAW_ENTITY[at - 1];
                DRAW_DIST[at] = DRAW_DIST[at - 1];
                at--;
            }
            DRAW[at] = s;
            DRAW_ENTITY[at] = le;
            DRAW_DIST[at] = dist;
        }
        if (n == 0) return;

        LEFT.set(camera.getLeftVector());
        UP.set(camera.getUpVector());
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float t = ((mc.level.getGameTime() % 72000L) + pt) / 20f;
        boolean shader = AuraShaders.use();
        if (shader) {
            PoseStack mv = RenderSystem.getModelViewStack();
            mv.pushPose();
            mv.mulPoseMatrix(pose.last().pose());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableCull();
        }
        try {
            for (int i = 0; i < n; i++) {
                LivingEntity e = DRAW_ENTITY[i];
                State s = DRAW[i];
                float ox = (float) (Mth.lerp(pt, e.xo, e.getX()) - cx), oy = (float) (Mth.lerp(pt, e.yo, e.getY()) - cy),
                        oz = (float) (Mth.lerp(pt, e.zo, e.getZ()) - cz);
                boolean eyes = e == mc.player && !camera.isDetached();          // your own, from your eyes: particles only
                draw(s, e, pt, ox, oy, oz, t, DRAW_DIST[i], shader, eyes, pose, buffers);
            }
        } finally {
            if (shader) {
                RenderSystem.getModelViewStack().popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            }
        }

        // every particle in one batch, with the plain view again
        int budget = MAX_PARTICLES;
        int detail = DBZConfig.CLIENT.auraDetail.get();
        VertexConsumer stars = buffers.getBuffer(FxRenderTypes.additive(STAR));
        for (int i = 0; i < n && budget > 0; i++) {
            State s = DRAW[i];
            if (s.drawFade > 0.01f && s.shellsDrawn && DRAW_DIST[i] < 64) budget -= sparkles(pose, stars, s, budget, detail);
        }
        buffers.endBatch(FxRenderTypes.additive(STAR));
        VertexConsumer dots = buffers.getBuffer(FxRenderTypes.additive(DOT));
        for (int i = 0; i < n && budget > 0; i++) {
            State s = DRAW[i];
            if (s.drawFade > 0.01f && s.drawCharge > 0.02f && DRAW_DIST[i] < 64) budget -= motes(pose, dots, s, budget, detail, camera);
        }
        buffers.endBatch(FxRenderTypes.additive(DOT));
        for (int i = 0; i < n; i++) {
            DRAW[i] = null;
            DRAW_ENTITY[i] = null;
        }
    }

    /** How tall the fighter stands, unscaled by crouching. */
    static float bodyHeight(LivingEntity e) {
        if (e instanceof Player p) return 1.8f * com.dbzenith.appearance.Stature.heightScale(p) * com.dbzenith.transform.GreatApe.scaleOf(p);
        return e.getBbHeight();
    }

    private static void draw(State s, LivingEntity e, float pt, float ox, float oy, float oz, float t, float dist, boolean shader,
                             boolean eyes, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        AuraDef d = s.def;
        AuraDef.React rc = d.react;
        float k = Mth.lerp(pt, s.strengthO, s.strength);
        float charge = Mth.lerp(pt, s.chargeO, s.charge);
        float flare = Mth.lerp(pt, s.flareO, s.flare);
        float burst = Mth.lerp(pt, s.burstO, s.burst);
        float ph = Mth.lerp(pt, s.phaseO, s.phase);
        float vx = Mth.lerp(pt, s.vxO, s.vx), vy = Mth.lerp(pt, s.vyO, s.vy), vz = Mth.lerp(pt, s.vzO, s.vz);
        float body = bodyHeight(e);

        float pulse = 1 + d.pulse * Mth.sin(ph * d.pulseSpeed + s.seed);
        float grow = 0.55f + 0.45f * k;                                            // it swells out as it comes
        float size = pulse * grow * (1 + rc.chargeScale * charge + rc.hitScale * flare + rc.burstScale * burst);
        float tall = pulse * (0.65f + 0.35f * k) * (1 + rc.chargeHeight * charge + rc.hitScale * flare + rc.burstScale * burst);
        float wild = 1 + rc.chargeWild * charge + 0.8f * flare + burst;
        float bright = rc.chargeGlow * charge + rc.hitBright * flare + rc.burstBright * burst;
        float radius = d.width * 0.5f * body * size;
        float height = d.height * body * tall;
        float bottom = d.bottom * body;
        float peak = d.peak * (1 + 0.15f * Mth.sin(ph * 6.1f + s.seed) + 0.1f * Mth.sin(ph * 13.7f + s.seed * 2) + 0.35f * charge);

        // charging shakes it
        float shake = (rc.chargeShake * charge + 0.012f * flare) * body;
        if (shake > 0) {
            ox += shake * (0.6f * Mth.sin(t * 37f + s.seed) + 0.4f * Mth.sin(t * 23.7f + s.seed * 2));
            oy += shake * 0.4f * Mth.sin(t * 29.3f + s.seed * 3);
            oz += shake * (0.6f * Mth.cos(t * 33f + s.seed) + 0.4f * Mth.sin(t * 19.1f + s.seed * 5));
        }
        // moving fast streams it back
        float speed = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        float tx = 0, ty = 0, tz = 0;
        if (speed > 0.12f) {
            float len = Math.min((speed - 0.12f) * rc.trail * body, rc.trailMax * body);
            tx = -vx / speed * len;
            ty = -vy / speed * len;
            tz = -vz / speed * len;
        }

        int detail = DBZConfig.CLIENT.auraDetail.get();
        float lod = dist < 14 ? 1f : dist < 36 ? 0.62f : 0.4f;
        if (detail == 0) lod *= 0.65f;
        if (detail == 2) lod = Math.min(1f, lod * 1.3f);
        int rings = Math.max(8, Math.round(28 * lod)), segs = Math.max(12, Math.round(40 * lod));
        float fade = k;
        if (ox * ox + oz * oz < radius * radius * 1.2f && -oy > bottom && -oy < bottom + height) fade *= 0.25f;   // the eye inside the shell

        s.drawX = ox;
        s.drawY = oy;
        s.drawZ = oz;
        s.drawRadius = radius;
        s.drawHeight = height;
        s.drawBottom = bottom;
        s.drawBody = body;
        s.drawFade = eyes ? k * 0.5f : fade;
        s.drawCharge = charge;
        s.drawPhase = ph;
        s.shellsDrawn = !eyes;
        if (eyes) return;

        List<AuraDef.Layer> layers = d.layers;
        int built = -1;
        for (int i = 0; i < layers.size(); i++) {
            AuraDef.Layer l = layers.get(i);
            boolean glow = l.kind == AuraDef.Layer.GLOW;
            AuraDef.Layer g = glow ? d.wrapped(i) : l;                            // whose outline it is
            if (g == null) continue;
            if (l.kind == AuraDef.Layer.HAZE && detail == 0 && dist > 14) continue;
            int gi = layers.indexOf(g);
            if (built != gi) {
                boolean haze = g.kind == AuraDef.Layer.HAZE;
                float lr = radius * g.scale, lh = height * g.heightScale, lb = bottom * g.scale + g.lift * height;
                int lrings = haze ? Math.max(8, rings * 3 / 4) : rings, lsegs = haze ? Math.max(12, segs * 3 / 4) : segs;
                SHELL.build(d, lr, lh, lb, ph * g.speed, s.seed + g.seed, lrings, lsegs, d.lobeSize * g.lobes * (1 + 0.4f * (wild - 1)),
                        d.sway * g.sway, peak, tx * g.scale, ty * g.scale, tz * g.scale);
                built = gi;
            }
            float scale = glow ? l.scale : 1f;
            float opacity = Mth.clamp(l.opacity * fade, 0, 1);
            if (shader) {
                ShaderInstance sh = l.additive ? AuraShaders.glow : AuraShaders.shell;
                uniforms(sh, l, g, s.seed + g.seed, opacity, ph * g.speed, wild, bright);
                drawShell(l.additive ? GLOW_SHADER : SHELL_SHADER, ox, oy, oz, scale);
            } else {
                var type = l.additive ? FxRenderTypes.auraPlainGlow(WHITE) : FxRenderTypes.auraPlain(WHITE);
                float lit = opacity * (1 + 0.3f * bright);
                if (glow) {
                    SHELL.emitPlain(buffers.getBuffer(type), pose.last().pose(), pose.last().normal(), ox, oy, oz, scale, true,
                            l.core, l.mid, l.rim, l.band(), 0, lit);
                } else {
                    SHELL.emitPlain(buffers.getBuffer(type), pose.last().pose(), pose.last().normal(), ox, oy, oz, 1f, false,
                            l.core, l.mid, l.edge, l.coreAlpha, l.edgeAlpha, lit);
                }
                buffers.endBatch(type);
            }
        }
    }

    private static void drawShell(Supplier<ShaderInstance> shader, float ox, float oy, float oz, float scale) {
        RenderSystem.setShader(shader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        SHELL.emit(bb, ox, oy, oz, scale, 255);
        BufferUploader.drawWithShader(bb.end());
    }

    /** One layer's look: {@code l} the layer drawn, {@code g} the shell whose outline it takes (itself, or a glow's). */
    private static void uniforms(ShaderInstance sh, AuraDef.Layer l, AuraDef.Layer g, float seed, float opacity, float t, float wild, float bright) {
        boolean glow = l.kind == AuraDef.Layer.GLOW;
        set(sh, "AuraCore", l.core, l.coreAlpha);
        set(sh, "AuraMid", l.mid, 1f);
        set(sh, "AuraEdge", l.edge, l.edgeAlpha);
        set(sh, "AuraRim", l.rim, l.rimStrength);
        float spikes = Math.min(1.8f, 1 + 0.3f * (wild - 1));
        sh.safeGetUniform("AuraShape").set(g.spikeSize * spikes, g.spikeSharpness, (float) g.spikeCount, glow ? l.band() : l.rimWidth);
        sh.safeGetUniform("AuraMotion").set(t, g.scroll, l.streaks, l.flicker * wild);
        sh.safeGetUniform("AuraMode").set((float) l.kind, seed, opacity, g.spikeLean);
        sh.safeGetUniform("AuraBoost").set(bright, 0f, 0f, 0f);
    }

    private static void set(ShaderInstance sh, String name, int rgb, float a) {
        sh.safeGetUniform(name).set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a);
    }

    // ------------------------------------------------------------------ particles

    /**
     * Points of light through the aura while it burns. Sparkles: star dust rising through the shell, twinkling and
     * winking out. Embers: sparks drifting up and out past the top, fading as they cool. Returns how many it drew.
     */
    private static int sparkles(PoseStack pose, VertexConsumer vc, State st, int budget, int detail) {
        AuraDef d = st.def;
        boolean ember = "ember".equals(d.particles);
        if (!ember && !"sparkle".equals(d.particles) || d.particleRate <= 0) return 0;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float t = st.drawPhase, seed = st.seed, fade = st.drawFade;
        int count = Math.min(budget, Math.round(18 * d.particleRate * (1 + st.drawCharge) * (detail == 0 ? 0.5f : detail == 2 ? 1.4f : 1f)));
        int r = (d.particleColor >> 16) & 255, g = (d.particleColor >> 8) & 255, b = d.particleColor & 255;
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            float period = (ember ? 1.0f : 1.4f) + (i % 5) * 0.35f;
            float phase = (t + i * 0.731f + seed) / period;
            int cycle = Mth.floor(phase);
            float life = phase - cycle;
            int h = hash((int) (seed * 1000) + i * 7919 + cycle * 104729);
            float a = (h & 1023) / 1023f * Mth.TWO_PI;
            float in = 0.25f + 0.7f * ((h >>> 10) & 1023) / 1023f;
            float s0 = 0.05f + 0.35f * ((h >>> 20) & 1023) / 1023f;
            float s = ember ? s0 + life * 1.1f : Math.min(1f, s0 + life * 0.75f);
            float rr = AuraShell.profile(d, Math.min(1f, s)) * st.drawRadius * in * (ember ? 1 + 0.5f * life : 1);
            a += ember ? 0.6f * Mth.sin(life * 5 + i) : 0;
            float px = st.drawX + Mth.cos(a) * rr, py = st.drawY + st.drawBottom + s * st.drawHeight, pz = st.drawZ + Mth.sin(a) * rr;
            float size = st.drawBody * 0.045f * d.particleSize * (0.6f + 0.8f * ((h >>> 5) & 255) / 255f) * (ember ? 1 - 0.6f * life : 1);
            float twinkle = ember ? 0.8f + 0.2f * Mth.sin(t * 23f + i) : 0.6f + 0.4f * Mth.sin(t * 17f + i * 2.3f);
            int alpha = Mth.clamp((int) (255 * Mth.sin(Mth.PI * life) * twinkle * fade), 0, 255);
            if (alpha < 4) continue;
            billboard(vc, m, nm, px, py, pz, size, r, g, b, alpha);
            drawn++;
        }
        return drawn;
    }

    /** Energy motes rising from the ground round a charging fighter, stretched by their speed. Returns how many it drew. */
    private static int motes(PoseStack pose, VertexConsumer vc, State st, int budget, int detail, Camera camera) {
        AuraDef d = st.def;
        if (d.moteRate <= 0) return 0;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float t = st.drawPhase, seed = st.seed;
        float c = st.drawCharge;
        int count = Math.min(budget, Math.round(22 * d.moteRate * c * (detail == 0 ? 0.5f : detail == 2 ? 1.5f : 1f)));
        int r = (d.moteColor >> 16) & 255, g = (d.moteColor >> 8) & 255, b = d.moteColor & 255;
        int drawn = 0;
        boolean eyes = !st.shellsDrawn;
        for (int i = 0; i < count; i++) {
            float period = 0.7f + (i % 7) * 0.12f;
            float phase = (t * 1.3f + i * 0.377f + seed * 0.5f) / period;
            int cycle = Mth.floor(phase);
            float life = phase - cycle;
            int h = hash((int) (seed * 977) + i * 6151 + cycle * 92821);
            float a = (h & 1023) / 1023f * Mth.TWO_PI;
            float out = (0.55f + 0.75f * ((h >>> 10) & 1023) / 1023f) * st.drawRadius * (1 - 0.35f * life);
            if (eyes) out = Math.max(out, st.drawBody * 0.55f);                  // never through your own eyes
            float px = st.drawX + Mth.cos(a) * out, pz = st.drawZ + Mth.sin(a) * out;
            float py = st.drawY + st.drawBottom + life * life * st.drawHeight * 1.1f;
            float w = st.drawBody * 0.018f * d.moteSize * (0.7f + 0.6f * ((h >>> 20) & 255) / 255f);
            float len = w * (3 + 9 * life);                                       // they speed up as they rise
            int alpha = Mth.clamp((int) (230 * Mth.sin(Mth.PI * life) * st.drawFade * Math.min(1, c * 1.5f)), 0, 255);
            if (alpha < 4) continue;
            float sx = pz, sz = -px;                                              // across the line of sight, level
            float sl = Mth.sqrt(sx * sx + sz * sz);
            if (sl < 1e-4f) continue;
            sx = sx / sl * w;
            sz = sz / sl * w;
            corner(vc, m, nm, px - sx, py - len, pz - sz, 0, 1, r, g, b, alpha);
            corner(vc, m, nm, px + sx, py - len, pz + sz, 1, 1, r, g, b, alpha);
            corner(vc, m, nm, px + sx, py + len, pz + sz, 1, 0, r, g, b, alpha);
            corner(vc, m, nm, px - sx, py + len, pz - sz, 0, 0, r, g, b, alpha);
            drawn++;
        }
        return drawn;
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, Matrix3f nm, float px, float py, float pz, float size, int r, int g, int b, int alpha) {
        float lx = LEFT.x() * size, ly = LEFT.y() * size, lz = LEFT.z() * size;
        float ux = UP.x() * size, uy = UP.y() * size, uz = UP.z() * size;
        corner(vc, m, nm, px + lx - ux, py + ly - uy, pz + lz - uz, 0, 1, r, g, b, alpha);
        corner(vc, m, nm, px - lx - ux, py - ly - uy, pz - lz - uz, 1, 1, r, g, b, alpha);
        corner(vc, m, nm, px - lx + ux, py - ly + uy, pz - lz + uz, 1, 0, r, g, b, alpha);
        corner(vc, m, nm, px + lx + ux, py + ly + uy, pz + lz + uz, 0, 0, r, g, b, alpha);
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0, 1, 0).endVertex();
    }

    private static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    // ------------------------------------------------------------------ /dbzaura

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dbzaura")
                .then(Commands.literal("off").executes(ctx -> {
                    preview = null;
                    devState = "idle";
                    tell(Component.translatable("message.dbzenith.aura_preview_off"));
                    return 1;
                }))
                .then(Commands.literal("state")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("idle", "charge", "fly", "hit", "burst", "hithold", "bursthold"), b))
                                .executes(ctx -> {
                                    devState = StringArgumentType.getString(ctx, "state");
                                    tell(Component.literal("Aura state: " + devState));
                                    return 1;
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> tweak(StringArgumentType.getString(ctx, "path"), StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("dump").executes(ctx -> dump()))
                .then(Commands.argument("aura", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(AuraDefs.ids(), b))
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "aura");
                            AuraDef d = AuraDefs.byId(id);
                            if (d == null) {
                                tell(Component.translatable("message.dbzenith.aura_unknown", id));
                                return 0;
                            }
                            preview = d;
                            tell(Component.translatable("message.dbzenith.aura_preview", id));
                            return 1;
                        })));
    }

    /** The aura you are looking at: the one you preview, else the one your form wears. */
    private static AuraDef current() {
        if (preview != null) return preview;
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : wanted(ClientPublicStates.get(mc.player.getId()));
    }

    /** /dbzaura set layers.1.spikes.size 0.2: changes one value of the aura you look at, live, until F3+T. */
    private static int tweak(String path, String value) {
        AuraDef d = current();
        if (d == null) {
            tell(Component.literal("No aura to tweak: wear one with /dbzaura <id>"));
            return 0;
        }
        JsonObject json = d.source.deepCopy();
        try {
            String[] keys = path.split("\\.");
            JsonElement at = json;
            for (int i = 0; i < keys.length - 1; i++) at = step(at, keys[i], keys[i + 1].matches("\\d+"));
            put(at, keys[keys.length - 1], literal(value));
            AuraDef changed = AuraDef.parse(d.id, json);
            AuraDefs.replace(changed);
            if (preview == d) preview = changed;
            for (State s : STATES.values()) if (s.def == d) s.def = changed;
            tell(Component.literal(d.id + ": " + path + " = " + value));
            return 1;
        } catch (Exception ex) {
            tell(Component.literal("Can't set " + path + ": " + ex.getMessage()));
            return 0;
        }
    }

    private static JsonElement step(JsonElement at, String key, boolean nextIsIndex) {
        if (at.isJsonArray()) {
            JsonArray arr = at.getAsJsonArray();
            int i = Integer.parseInt(key);
            while (arr.size() <= i) arr.add(new JsonObject());
            return arr.get(i);
        }
        JsonObject o = at.getAsJsonObject();
        if (!o.has(key) || o.get(key).isJsonPrimitive()) o.add(key, nextIsIndex ? new JsonArray() : new JsonObject());
        return o.get(key);
    }

    private static void put(JsonElement at, String key, JsonElement value) {
        if (at.isJsonArray()) {
            JsonArray arr = at.getAsJsonArray();
            int i = Integer.parseInt(key);
            while (arr.size() <= i) arr.add(new JsonObject());
            arr.set(i, value);
        } else {
            at.getAsJsonObject().add(key, value);
        }
    }

    private static JsonElement literal(String v) {
        v = v.trim();
        if (v.equals("true") || v.equals("false")) return new JsonPrimitive(Boolean.parseBoolean(v));
        try {
            return new JsonPrimitive(Double.parseDouble(v));
        } catch (NumberFormatException ignored) {
            return new JsonPrimitive(v.startsWith("\"") && v.endsWith("\"") && v.length() > 1 ? v.substring(1, v.length() - 1) : v);
        }
    }

    /** /dbzaura dump: writes the aura you look at, tweaks and all, to aura_tweaks/<id>.json in the game folder. */
    private static int dump() {
        AuraDef d = current();
        if (d == null) {
            tell(Component.literal("No aura to dump: wear one with /dbzaura <id>"));
            return 0;
        }
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("aura_tweaks");
            Files.createDirectories(dir);
            Path file = dir.resolve(d.id.replace(':', '_') + ".json");
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(d.source, w);
            }
            tell(Component.literal("Saved " + file));
            DBZenith.LOGGER.info("[aura] saved {}", file);
            return 1;
        } catch (Exception ex) {
            tell(Component.literal("Can't save: " + ex.getMessage()));
            return 0;
        }
    }

    private static void tell(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(c, true);
    }

    /** For the dev screenshots: wear an aura by id (null or "off" to stop). */
    public static void devPreview(String id) {
        preview = id == null || id.equals("off") ? null : AuraDefs.byId(id);
    }

    /**
     * For the dev screenshots: a shot named {@code ..aurapv.<id>.<state>..} wears that aura in that state, and
     * {@code ..auraset.<path>+<value>_..} changes one value (dots in the path as dashes), for example
     * {@code auraset.layers-1-spikes-size+0.25}.
     */
    public static void devFromShot(String name) {
        int at = name.indexOf("aurapv.");
        if (at >= 0) {
            String[] parts = name.substring(at + 7).split("\\.");
            devPreview(parts.length > 0 ? parts[0] : "off");
            devState = parts.length > 1 && !parts[1].isEmpty() ? parts[1].replaceAll("_.*", "") : "idle";
        }
        int set = name.indexOf("auraset.");
        if (set >= 0) {
            String spec = name.substring(set + 8).split("_")[0];
            int eq = spec.indexOf('+');
            if (eq > 0) tweak(spec.substring(0, eq).replace('-', '.'), spec.substring(eq + 1));
        }
    }
}
