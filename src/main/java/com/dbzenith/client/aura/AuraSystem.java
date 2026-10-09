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
    private static final ResourceLocation RING = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/shock_ring.png");
    private static final Supplier<ShaderInstance> SHELL_SHADER = () -> AuraShaders.shell, GLOW_SHADER = () -> AuraShaders.glow;
    private static final int MAX_DRAWN = 64;
    private static final float MAX_DISTANCE = 160;
    /** At most this many particles a frame, for every aura together. */
    private static final int MAX_PARTICLES = 900;

    /** One aura a fighter wears: the form's, or a technique's over it. */
    static final class Slot {
        /** As worn: tinted to the fighter or set to the technique's stage. */
        AuraDef def;
        float strength, strengthO;
        /** A new aura (or a stage up) bursting out: 1, dying away. */
        float burst, burstO;
        int stage;
        // worked out while drawing, for the particles after
        float x, y, z, radius, height, bottom, body, fade, charge, phase, wild, seed, flash;
        boolean shells, drawn;

        void tick() {
            strengthO = strength;
            burstO = burst;
        }

        /** Moves towards wearing {@code want} at {@code vis}; a different aura waits for this one to go. */
        void follow(AuraDef want, float vis) {
            if (want != null && def != null && def != want && def.id.equals(want.id)) {   // the same aura, re-tinted or a new stage
                if (want.stageFrom != def.stageFrom || stage > 0) burst = Math.max(burst, 0.5f);
                def = want;
            }
            if (want != null && (def == null || def == want || strength <= 0.02f)) {
                if (def != want) {
                    def = want;
                    burst = 1;
                }
                strength = strength < vis ? Math.min(vis, strength + 0.14f) : Math.max(vis, strength - 0.12f);
            } else {
                strength = Math.max(0, strength - 0.12f);
            }
        }
    }

    /** What one fighter's aura is doing. */
    static final class State {
        final int id;
        final float seed;
        /** Each fighter's aura runs a little faster or slower, so two of the same never keep time. */
        final float pace;
        final Slot form = new Slot(), tech = new Slot();
        /** 0 calm .. 1 charging flat out. */
        float charge, chargeO;
        /** A hit or a swing: 1, dying away within a few ticks. */
        float flare, flareO;
        /** The aura's own clock (seconds), running faster the wilder it is, so speeding up never jumps. */
        float phase, phaseO;
        /** Smoothed movement, blocks a tick. */
        float vx, vy, vz, vxO, vyO, vzO;
        int lastHurt, lastStage;
        boolean lastSwing;
        long seen;
        /** The fighter's aura colour the form's aura was last tinted to, and that aura. */
        int tintColor = -1;
        AuraDef tintFrom, tinted;

        State(int id) {
            this.id = id;
            this.seed = (id * 0.6180339f) % 1f * 97f;
            this.pace = 0.9f + 0.2f * ((id * 0.7548777f) % 1f);
        }

        boolean active() {
            return form.strength > 0 || form.strengthO > 0 || tech.strength > 0 || tech.strengthO > 0;
        }
    }

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();
    private static final AuraShell SHELL = new AuraShell();
    private static final State[] DRAW = new State[MAX_DRAWN];
    private static final LivingEntity[] DRAW_ENTITY = new LivingEntity[MAX_DRAWN];
    private static final float[] DRAW_DIST = new float[MAX_DRAWN];
    private static final Vector3f LEFT = new Vector3f(), UP = new Vector3f();

    /** /dbzaura: wear this aura yourself, whatever your form (null: off), and a technique's at a stage over it. */
    static AuraDef preview, previewTech;
    static int previewStage;
    /** /dbzaura state: hold your own aura in a state to look at it (idle, charge, fly, hit, burst). */
    static String devState = "idle";
    private static int devTicks;

    private AuraSystem() {}

    /** The aura a fighter's form wears (the base form's only shows while charging), or null. */
    public static AuraDef wanted(PublicStatePacket state) {
        if (state == null) return null;
        return AuraDefs.forForm(state.form());
    }

    /** The technique aura a fighter wears over the form's (Kaioken), or null. */
    public static AuraDef wantedTechnique(PublicStatePacket state) {
        if (state == null || state.kaiokenStage() <= 0) return null;
        AuraDef k = AuraDefs.forTechnique("kaioken");
        return k == null ? null : k.forStage(state.kaiokenStage());
    }

    /** Is this fighter's aura drawn here (so the old renderer leaves it alone)? */
    public static boolean handles(Player player, PublicStatePacket state) {
        if (player == Minecraft.getInstance().player && (preview != null || previewTech != null)) return true;
        if (wanted(state) != null || wantedTechnique(state) != null) return true;
        State s = STATES.get(player.getId());
        return s != null && s.active();
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
            if (p.isSpectator()) continue;
            boolean self = p == mc.player;
            PublicStatePacket state = ClientPublicStates.get(p.getId());
            AuraDef form = self && preview != null ? preview : wanted(state);
            AuraDef tech = self && previewTech != null ? previewTech.forStage(previewStage) : wantedTechnique(state);
            State s = STATES.get(p.getId());
            if (s == null) {
                if (form == null && tech == null) continue;
                s = new State(p.getId());
                STATES.put(p.getId(), s);
            }
            s.seen = now;
            if (form != null && form.followsFighter && state != null) form = tintedFor(s, form, state.auraColor());
            int stage = self && previewTech != null ? previewStage : state == null ? 0 : state.kaiokenStage();
            follow(s, p, form, tech, stage, state, self);
        }
        ObjectIterator<State> it = STATES.values().iterator();
        while (it.hasNext()) {
            State s = it.next();
            if (now - s.seen > 2 || !s.active() && s.seen != now) it.remove();
        }
    }

    /** The form's aura in the fighter's own aura colour, made once per colour. */
    private static AuraDef tintedFor(State s, AuraDef def, int rgb) {
        if (s.tintFrom != def || s.tintColor != rgb || s.tinted == null) {
            s.tinted = def.tinted(rgb);
            s.tintFrom = def;
            s.tintColor = rgb;
        }
        return s.tinted;
    }

    private static void follow(State s, LivingEntity e, AuraDef form, AuraDef tech, int stage, PublicStatePacket state, boolean self) {
        s.form.tick();
        s.tech.tick();
        s.chargeO = s.charge;
        s.flareO = s.flare;
        s.phaseO = s.phase;
        s.vxO = s.vx;
        s.vyO = s.vy;
        s.vzO = s.vz;

        boolean dev = self && (preview != null || previewTech != null);
        boolean charging = state != null && (state.powering() || state.has(PublicStatePacket.TRANSFORMING));
        if (dev && devState.equals("charge")) charging = true;
        float target = charging ? 1 : 0;
        s.charge += (target - s.charge) * (target > s.charge ? 0.2f : 0.09f);

        // the base form's aura only burns while charging
        float vis = form == null || form.idle ? 1 : Mth.clamp(s.charge * 1.6f, 0, 1);
        s.form.follow(form, vis);
        s.tech.follow(tech, 1);
        if (stage > s.lastStage && s.lastStage > 0) s.tech.burst = Math.max(s.tech.burst, 0.6f);   // a Kaioken stage up flares
        s.lastStage = stage;
        s.tech.stage = stage;

        float dx = (float) (e.getX() - e.xo), dy = (float) (e.getY() - e.yo), dz = (float) (e.getZ() - e.zo);
        if (dev && devState.equals("fly")) {                                    // as if flying fast
            Vec3 look = Vec3.directionFromRotation(0, e.getYRot() + 90);       // sideways, so the trail shows from the front
            dx = (float) look.x * 1.4f;
            dy = 0;
            dz = (float) look.z * 1.4f;
        }
        s.vx += (dx - s.vx) * 0.6f;                                             // quick, so it never lags behind
        s.vy += (dy - s.vy) * 0.6f;
        s.vz += (dz - s.vz) * 0.6f;

        if (e.hurtTime > s.lastHurt) s.flare = 1;                               // just hit
        s.lastHurt = e.hurtTime;
        if (e.swinging && !s.lastSwing) s.flare = Math.max(s.flare, 0.6f);      // just swung
        s.lastSwing = e.swinging;
        if (dev && devState.equals("hit") && devTicks % 20 == 0) s.flare = 1;
        if (dev && devState.equals("burst") && devTicks % 40 == 0) s.form.burst = s.tech.burst = 1;
        if (dev && devState.equals("hithold")) s.flare = 0.8f;
        else s.flare *= 0.72f;
        if (dev && devState.equals("bursthold")) s.form.burst = s.tech.burst = 0.55f;
        else {
            s.form.burst = Math.max(0, s.form.burst - 0.06f);
            s.tech.burst = Math.max(0, s.tech.burst - 0.06f);
        }

        AuraDef lead = s.tech.def != null && s.tech.strength > 0 ? s.tech.def : s.form.def;
        if (lead != null) {
            float wild = 1 + lead.react.chargeWild * s.charge + 0.8f * s.flare + Math.max(s.form.burst, s.tech.burst)
                    + lead.growWild * Math.max(0, stage - lead.stageFrom);
            s.phase += 0.05f * wild * s.pace * lead.speed;
            if (s.phase > 3600) {                                               // keep the clock small enough to stay smooth
                s.phase -= 3600;
                s.phaseO -= 3600;
            }
        }
    }

    // ------------------------------------------------------------------ each frame: draw

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        // after the clouds and the weather: drawn before them, the clouds were painted over the aura and showed through
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || STATES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera camera = event.getCamera();
        float pt = event.getPartialTick();
        double cx = camera.getPosition().x, cy = camera.getPosition().y, cz = camera.getPosition().z;

        int n = 0;
        for (State s : STATES.values()) {
            s.form.drawn = s.tech.drawn = false;
            float k = Math.max(Mth.lerp(pt, s.form.strengthO, s.form.strength), Mth.lerp(pt, s.tech.strengthO, s.tech.strength));
            if (k <= 0.001f || n >= MAX_DRAWN) continue;
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
        LOOK.set(camera.getLookVector());
        // At this stage Minecraft has already put the camera's turn on its model-view matrix (for the clouds and the
        // weather), so everything here is drawn with an identity pose; multiplying the stage's pose in again turned
        // the camera twice and threw the aura off the screen.
        PoseStack pose = IDENTITY;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float t = ((mc.level.getGameTime() % 72000L) + pt) / 20f;
        boolean shader = AuraShaders.use();
        if (shader) {
            RenderSystem.getModelViewStack().pushPose();
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

        // every particle in one batch per texture, with the plain view again
        int budget = MAX_PARTICLES;
        int detail = DBZConfig.CLIENT.auraDetail.get();
        VertexConsumer stars = buffers.getBuffer(FxRenderTypes.additive(STAR));
        for (int i = 0; i < n && budget > 0; i++) {
            if (DRAW_DIST[i] >= 64) continue;
            for (Slot sl : slots(DRAW[i])) if (sl.drawn && sl.shells && sl.fade > 0.01f) budget -= sparkles(pose, stars, sl, budget, detail);
        }
        buffers.endBatch(FxRenderTypes.additive(STAR));
        VertexConsumer dots = buffers.getBuffer(FxRenderTypes.additive(DOT));
        for (int i = 0; i < n && budget > 0; i++) {
            if (DRAW_DIST[i] >= 64) continue;
            for (Slot sl : slots(DRAW[i])) {
                if (!sl.drawn || sl.fade <= 0.01f) continue;
                if (sl.charge > 0.02f) budget -= motes(pose, dots, sl, budget, detail);
                if (sl.shells && "flecks".equals(sl.def.particles)) budget -= flecks(pose, dots, sl, budget, detail);
                if (sl.shells && sl.def.lightningRate > 0) budget -= lightning(pose, dots, sl, t, budget, detail);
            }
        }
        buffers.endBatch(FxRenderTypes.additive(DOT));
        VertexConsumer rings = buffers.getBuffer(FxRenderTypes.additive(RING));
        for (int i = 0; i < n; i++) {
            Slot sl = DRAW[i].form.drawn ? DRAW[i].form : DRAW[i].tech;
            if (sl.drawn && DRAW_DIST[i] < 48) ground(pose, rings, DRAW[i], sl, t);
        }
        buffers.endBatch(FxRenderTypes.additive(RING));
        for (int i = 0; i < n; i++) {
            DRAW[i] = null;
            DRAW_ENTITY[i] = null;
        }
    }

    private static final PoseStack IDENTITY = new PoseStack();
    private static final Slot[] SLOTS = new Slot[2];

    /** A fighter's two auras, technique first (no list is made). */
    private static Slot[] slots(State s) {
        SLOTS[0] = s.tech;
        SLOTS[1] = s.form;
        return SLOTS;
    }

    /** How tall the fighter stands, unscaled by crouching. */
    static float bodyHeight(LivingEntity e) {
        if (e instanceof Player p) return 1.8f * com.dbzenith.appearance.Stature.heightScale(p) * com.dbzenith.transform.GreatApe.scaleOf(p);
        return e.getBbHeight();
    }

    /**
     * One fighter: works out both auras' sizes (a technique's wraps the form's), then draws the technique's (outside)
     * and the form's.
     */
    private static void draw(State s, LivingEntity e, float pt, float ox, float oy, float oz, float t, float dist, boolean shader,
                             boolean eyes, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        float charge = Mth.lerp(pt, s.chargeO, s.charge);
        float flare = Mth.lerp(pt, s.flareO, s.flare);
        float ph = Mth.lerp(pt, s.phaseO, s.phase);
        float vx = Mth.lerp(pt, s.vxO, s.vx), vy = Mth.lerp(pt, s.vyO, s.vy), vz = Mth.lerp(pt, s.vzO, s.vz);
        float body = bodyHeight(e);
        float formK = s.form.def == null ? 0 : Mth.lerp(pt, s.form.strengthO, s.form.strength);
        float techK = s.tech.def == null ? 0 : Mth.lerp(pt, s.tech.strengthO, s.tech.strength);
        if (formK > 0.001f) measure(s, s.form, formK, Mth.lerp(pt, s.form.burstO, s.form.burst), charge, flare, ph, body, 1, 1, 0, 0, 0);
        if (techK > 0.001f) {
            int over = Math.max(0, s.tech.stage - s.tech.def.stageFrom);
            float grow = 1 + s.tech.def.growScale * over, growH = 1 + s.tech.def.growHeight * over;
            // round the form's aura when there is one, else its own size
            float r = formK > 0.001f ? Mth.lerp(formK, 1f, s.form.radius * s.tech.def.wrapScale / (s.tech.def.width * 0.5f * body)) : 1f;
            float h = formK > 0.001f ? Mth.lerp(formK, 1f, s.form.height * s.tech.def.wrapHeight / (s.tech.def.height * body)) : 1f;
            measure(s, s.tech, techK, Mth.lerp(pt, s.tech.burstO, s.tech.burst), charge, flare, ph, body, r * grow, h * growH,
                    0.03f * over, 0, 0);
        }

        // charging shakes them, moving fast streams them back
        AuraDef lead = techK > 0.001f ? s.tech.def : s.form.def;
        if (lead == null) return;
        AuraDef.React rc = lead.react;
        float shake = (rc.chargeShake * charge + 0.012f * flare) * body;
        if (shake > 0) {
            ox += shake * (0.6f * Mth.sin(t * 37f + s.seed) + 0.4f * Mth.sin(t * 23.7f + s.seed * 2));
            oy += shake * 0.4f * Mth.sin(t * 29.3f + s.seed * 3);
            oz += shake * (0.6f * Mth.cos(t * 33f + s.seed) + 0.4f * Mth.sin(t * 19.1f + s.seed * 5));
        }
        // only real speed (flying, dashing) streams it back, a little; walking and running leave it upright round you
        float speed = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        float tx = 0, ty = 0, tz = 0;
        if (speed > 0.45f) {
            float len = Math.min((speed - 0.45f) * rc.trail * 0.6f * body, rc.trailMax * 0.5f * body);
            tx = -vx / speed * len;
            ty = -vy / speed * len;
            tz = -vz / speed * len;
        }
        if (techK > 0.001f) drawSlot(s, s.tech, ox, oy, oz, ph, dist, tx, ty, tz, shader, eyes, pose, buffers);
        if (formK > 0.001f) drawSlot(s, s.form, ox, oy, oz, ph, dist, tx, ty, tz, shader, eyes, pose, buffers);
    }

    /** Works out one aura's size and mood this frame into its slot. */
    private static void measure(State s, Slot sl, float k, float burst, float charge, float flare, float ph, float body,
                                float widthMul, float heightMul, float extraWild, float unused1, float unused2) {
        AuraDef d = sl.def;
        AuraDef.React rc = d.react;
        float pulse = 1 + d.pulse * Mth.sin(ph * d.pulseSpeed + s.seed);
        float grow = 0.55f + 0.45f * k;                                          // it swells out as it comes
        // it never breathes in and out like a balloon: the pulse and most of a charge stretch it upward, the width
        // barely moves
        float size = grow * (1 + 0.35f * rc.chargeScale * charge + 0.4f * rc.hitScale * flare + 0.5f * rc.burstScale * burst);
        float tall = (1 + 1.5f * (pulse - 1)) * (0.65f + 0.35f * k) * (1 + rc.chargeHeight * charge + rc.hitScale * flare + rc.burstScale * burst);
        sl.wild = 1 + rc.chargeWild * charge + 0.8f * flare + burst + extraWild;
        sl.charge = charge;
        sl.radius = d.width * 0.5f * body * size * widthMul;
        sl.height = d.height * body * tall * heightMul;
        sl.bottom = d.bottom * body;
        sl.body = body;
        sl.fade = k;
        sl.phase = ph;
        sl.seed = s.seed + (sl == s.tech ? 41.7f : 0);
        sl.flash = burst;
    }

    private static void drawSlot(State s, Slot sl, float ox, float oy, float oz, float ph, float dist, float tx, float ty, float tz,
                                 boolean shader, boolean eyes, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        AuraDef d = sl.def;
        AuraDef.React rc = d.react;
        float radius = sl.radius, height = sl.height, bottom = sl.bottom;
        float wild = sl.wild;
        float bright = rc.chargeGlow * sl.charge + rc.hitBright * s.flare + rc.burstBright * sl.flash;
        float peak = d.peak * (1 + 0.08f * Mth.sin(ph * 3.1f + sl.seed) + 0.04f * Mth.sin(ph * 5.3f + sl.seed * 2) + 0.35f * sl.charge);   // a slow rise and fall, no quiver
        int detail = DBZConfig.CLIENT.auraDetail.get();
        float lod = dist < 14 ? 1f : dist < 36 ? 0.62f : 0.4f;
        if (detail == 0) lod *= 0.65f;
        if (detail == 2) lod = Math.min(1f, lod * 1.3f);
        int rings = Math.max(8, Math.round(40 * lod)), segs = Math.max(12, Math.round(64 * lod));   // fine enough up close that the outline shows no facets
        float fade = sl.fade;
        if (-oy > bottom && -oy < bottom + height) {                            // the eye inside the shell: thin it out, smoothly
            float near = Mth.sqrt(ox * ox + oz * oz) / Math.max(0.1f, radius);
            fade *= 0.45f + 0.55f * AuraShell.smooth(0.6f, 1.05f, near);
        }
        centerX = ox;
        centerZ = oz;
        lean(radius, bottom, height, ox, oy, oz);

        sl.x = ox;
        sl.y = oy;
        sl.z = oz;
        sl.fade = eyes ? sl.fade * 0.5f : fade;
        sl.shells = !eyes;
        sl.drawn = true;
        if (eyes) return;

        List<AuraDef.Layer> layers = d.layers;
        int built = -1;
        for (int i = 0; i < layers.size(); i++) {
            AuraDef.Layer l = layers.get(i);
            if (l.kind == AuraDef.Layer.TONGUES) {
                int ref = mainShell(d);                                           // the flames follow the main shell's surface
                if (ref < 0) continue;
                if (built != ref) {
                    AuraDef.Layer g = layers.get(ref);
                    SHELL.build(d, radius * g.scale, height * g.heightScale, bottom * g.scale + g.lift * height, ph * g.speed, sl.seed + g.seed,
                            rings, segs, d.lobeSize * g.lobes * (1 + 0.4f * (wild - 1)), d.sway * g.sway, peak, tx * g.scale, ty * g.scale, tz * g.scale);
                    built = ref;
                }
                tongues(l, sl.seed, ox, oy, oz, height, ph * l.speed, wild, bright, Mth.clamp(l.opacity * fade, 0, 1), dist, detail, shader, pose, buffers);
                continue;
            }
            boolean glow = l.kind == AuraDef.Layer.GLOW;
            AuraDef.Layer g = glow ? d.wrapped(i) : l;                            // whose outline it is
            if (g == null) continue;
            if (l.kind == AuraDef.Layer.HAZE && detail == 0 && dist > 14) continue;
            int gi = layers.indexOf(g);
            if (built != gi) {
                boolean haze = g.kind == AuraDef.Layer.HAZE;
                float lr = radius * g.scale, lh = height * g.heightScale, lb = bottom * g.scale + g.lift * height;
                int lrings = haze ? Math.max(8, rings * 3 / 4) : rings, lsegs = haze ? Math.max(12, segs * 3 / 4) : segs;
                SHELL.build(d, lr, lh, lb, ph * g.speed, sl.seed + g.seed, lrings, lsegs, d.lobeSize * g.lobes * (1 + 0.4f * (wild - 1)),
                        d.sway * g.sway, peak, tx * g.scale, ty * g.scale, tz * g.scale);
                built = gi;
            }
            float scale = glow ? l.scale : 1f;
            float opacity = Mth.clamp(l.opacity * fade, 0, 1);
            if (shader) {
                ShaderInstance sh = l.additive ? AuraShaders.glow : AuraShaders.shell;
                uniforms(sh, l, g, sl.seed + g.seed, opacity, ph * g.speed, wild, bright);
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

    private static final ResourceLocation TONGUE_TEX = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/aura_tongue.png");

    /** The first flame shell of an aura, which its inner flames follow; -1 when it has none. */
    private static int mainShell(AuraDef d) {
        for (int i = 0; i < d.layers.size(); i++) if (d.layers.get(i).kind == AuraDef.Layer.SHELL) return i;
        return -1;
    }

    /** A layer of inner flames along the built shell: through the aura shader, or the old flame texture on the plain shaders. */
    private static void tongues(AuraDef.Layer l, float seed, float ox, float oy, float oz, float height, float t, float wild, float bright,
                                float opacity, float dist, int detail, boolean shader, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        if (dist > 48 || (detail == 0 && dist > 20)) return;
        int budget = detail == 0 ? Math.max(4, l.tongueCount / 2) : 64;
        if (shader) {
            uniforms(l.additive ? AuraShaders.glow : AuraShaders.shell, l, l, seed + l.seed, opacity, t, wild, bright);
            blend(l.additive);
            RenderSystem.setShader(l.additive ? GLOW_SHADER : SHELL_SHADER);
            RenderSystem.disableCull();
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            AuraFlames.emit(bb, null, SHELL, l, ox, oy, oz, height, t, wild, seed + l.seed, 1f, 0, 0, budget);
            BufferBuilder.RenderedBuffer done = bb.endOrDiscardIfEmpty();
            if (done != null) BufferUploader.drawWithShader(done);
            RenderSystem.enableCull();
        } else {
            var type = l.additive ? FxRenderTypes.additive(TONGUE_TEX) : FxRenderTypes.soft(TONGUE_TEX);
            AuraFlames.emit(buffers.getBuffer(type), pose.last().pose(), SHELL, l, ox, oy, oz, height, t, wild, seed + l.seed,
                    opacity * (1 + 0.3f * bright), AuraDef.mix(l.edge, l.mid, 0.35f), 16, budget);
            buffers.endBatch(type);
        }
    }

    /**
     * Sets the blending by hand: a shader's own blend mode is skipped when it was the last one applied, even if
     * something has turned blending off since, and then the aura would draw solid.
     */
    private static void blend(boolean additive) {
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
    }

    private static void drawShell(Supplier<ShaderInstance> shader, float ox, float oy, float oz, float scale) {
        blend(shader == GLOW_SHADER);
        RenderSystem.setShader(shader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        SHELL.emit(bb, ox, oy, oz, scale, 255);
        BufferUploader.drawWithShader(bb.end());
    }

    private static final org.joml.Vector3f LOOK = new org.joml.Vector3f();

    /**
     * Leans the aura away from the eye by part of the angle the eye looks down on it (CX-26), about a level axis through
     * its middle, so from above it still shows its flame outline round the fighter (upright, it flattened into a ring
     * round the feet with the tip floating over the head). Nothing goes below the feet: the bottom swings up towards the
     * eye. Off while the eye is inside or right at the aura, and when looking up at it.
     */
    private static void lean(float radius, float bottom, float height, float ox, float oy, float oz) {
        float my = oy + bottom + height * 0.42f;                                 // the pivot, camera-relative
        float hd = Mth.sqrt(ox * ox + oz * oz);
        float dx, dz;
        if (hd > 0.3f) {
            dx = ox / hd;
            dz = oz / hd;
        } else {                                                                 // straight overhead: the way the camera faces
            float l = Mth.sqrt(LOOK.x * LOOK.x + LOOK.z * LOOK.z);
            dx = l > 1e-3f ? LOOK.x / l : 0f;
            dz = l > 1e-3f ? LOOK.z / l : 1f;
        }
        float down = (float) Math.atan2(-my, Math.max(hd, 0.001f));            // how steeply the eye looks down on it
        float out = AuraShell.smooth(1.4f, 2.4f, Mth.sqrt(ox * ox + my * my + oz * oz) / Math.max(0.1f, radius));
        float angle = LEAN * Math.max(0f, down) * out;
        AuraShell.setTilt(ox, my, oz, dx, dz, angle);
    }

    /** How much of the eye's downward angle the aura leans by. */
    private static final float LEAN = 0.75f;

    /** The aura being drawn: its feet relative to the eye, for the shader to lay its flames out from the eye's side. */
    private static float centerX, centerZ;

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
        sh.safeGetUniform("AuraBoost").set(bright, g.tallFlames, g.streakSpeed, g.stretch);
        sh.safeGetUniform("AuraFlow").set(g.warp, l.kind == AuraDef.Layer.SHELL ? l.band : 0f, g.fps, g.smear);
        sh.safeGetUniform("AuraCenter").set(centerX, 0f, centerZ, 0f);
    }

    private static void set(ShaderInstance sh, String name, int rgb, float a) {
        sh.safeGetUniform(name).set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a);
    }

    // ------------------------------------------------------------------ particles

    /**
     * Points of light through the aura while it burns. Sparkles: star dust rising through the shell, twinkling and
     * winking out. Embers: sparks drifting up and out past the top, fading as they cool. Returns how many it drew.
     */
    private static int sparkles(PoseStack pose, VertexConsumer vc, Slot st, int budget, int detail) {
        AuraDef d = st.def;
        boolean ember = "ember".equals(d.particles);
        if (!ember && !"sparkle".equals(d.particles) || d.particleRate <= 0) return 0;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float t = st.phase, seed = st.seed, fade = st.fade;
        int count = Math.min(budget, Math.round(18 * d.particleRate * (1 + st.charge) * (detail == 0 ? 0.5f : detail == 2 ? 1.4f : 1f)));
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
            // both stay inside the aura: embers drift up and cool before the top instead of flying off past it
            float s = ember ? Math.min(0.92f, s0 + life * 0.8f) : Math.min(0.95f, s0 + life * 0.75f);
            float rr = AuraShell.profile(d, s) * st.radius * in * (ember ? 0.85f : 0.95f);
            a += ember ? 0.6f * Mth.sin(life * 5 + i) : 0;
            float px = st.x + Mth.cos(a) * rr, py = st.y + st.bottom + s * st.height, pz = st.z + Mth.sin(a) * rr;
            float size = st.body * (ember ? 0.03f : 0.022f) * d.particleSize * (0.6f + 0.8f * ((h >>> 5) & 255) / 255f) * (ember ? 1 - 0.6f * life : 1);
            float twinkle = ember ? 0.8f + 0.2f * Mth.sin(t * 23f + i) : 0.6f + 0.4f * Mth.sin(t * 17f + i * 2.3f);
            int alpha = Mth.clamp((int) (255 * Mth.sin(Mth.PI * life) * twinkle * fade), 0, 255);
            if (alpha < 4) continue;
            billboard(vc, m, nm, px, py, pz, size, r, g, b, alpha);
            drawn++;
        }
        return drawn;
    }

    /** Energy motes rising from the ground round a charging fighter, stretched by their speed. Returns how many it drew. */
    private static int motes(PoseStack pose, VertexConsumer vc, Slot st, int budget, int detail) {
        AuraDef d = st.def;
        if (d.moteRate <= 0) return 0;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float t = st.phase, seed = st.seed;
        float c = st.charge;
        int count = Math.min(budget, Math.round(22 * d.moteRate * c * (detail == 0 ? 0.5f : detail == 2 ? 1.5f : 1f)));
        int r = (d.moteColor >> 16) & 255, g = (d.moteColor >> 8) & 255, b = d.moteColor & 255;
        int drawn = 0;
        boolean eyes = !st.shells;
        for (int i = 0; i < count; i++) {
            float period = 0.7f + (i % 7) * 0.12f;
            float phase = (t * 1.3f + i * 0.377f + seed * 0.5f) / period;
            int cycle = Mth.floor(phase);
            float life = phase - cycle;
            int h = hash((int) (seed * 977) + i * 6151 + cycle * 92821);
            float a = (h & 1023) / 1023f * Mth.TWO_PI;
            // inside the aura's outline at the height they have reached, never scattered round it
            float sNow = life * life * 0.85f;
            float out = (0.3f + 0.55f * ((h >>> 10) & 1023) / 1023f) * AuraShell.profile(d, sNow) * st.radius;
            if (eyes) out = Math.max(out, Math.min(st.body * 0.55f, AuraShell.profile(d, sNow) * st.radius * 0.9f));   // not through your eyes
            float px = st.x + Mth.cos(a) * out, pz = st.z + Mth.sin(a) * out;
            float py = st.y + st.bottom + sNow * st.height;
            float w = st.body * 0.018f * d.moteSize * (0.7f + 0.6f * ((h >>> 20) & 255) / 255f);
            float len = w * (3 + 9 * life);                                       // they speed up as they rise
            int alpha = Mth.clamp((int) (230 * Mth.sin(Mth.PI * life) * st.fade * Math.min(1, c * 1.5f)), 0, 255);
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

    /**
     * Flecks: small streaks of light shooting up inside the aura and flickering out, the sparks of energy in the
     * hollow middle of an anime aura. Returns how many it drew.
     */
    private static int flecks(PoseStack pose, VertexConsumer vc, Slot st, int budget, int detail) {
        AuraDef d = st.def;
        if (d.particleRate <= 0) return 0;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float fps = d.layers.isEmpty() ? 0 : mainFps(d);
        float t = fps > 0 ? Mth.floor(st.phase * fps) / fps : st.phase, seed = st.seed;   // on the aura's drawing timing
        int count = Math.min(budget, Math.round(20 * d.particleRate * (1 + st.charge) * (detail == 0 ? 0.5f : detail == 2 ? 1.4f : 1f)));
        int r = (d.particleColor >> 16) & 255, g = (d.particleColor >> 8) & 255, b = d.particleColor & 255;
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            float period = 0.45f + (i % 6) * 0.08f;
            float phase = (t * 1.2f + i * 0.613f + seed) / period;
            int cycle = Mth.floor(phase);
            float life = phase - cycle;
            int h = hash((int) (seed * 733) + i * 9277 + cycle * 81233);
            float a = (h & 1023) / 1023f * Mth.TWO_PI;
            float s0 = 0.05f + 0.6f * ((h >>> 10) & 1023) / 1023f;
            float sNow = Math.min(0.92f, s0 + life * 0.35f);
            float rr = AuraShell.profile(d, sNow) * st.radius * (0.2f + 0.6f * ((h >>> 20) & 255) / 255f);
            float px = st.x + Mth.cos(a) * rr, pz = st.z + Mth.sin(a) * rr;
            float py = st.y + st.bottom + sNow * st.height;
            float w = st.body * 0.02f * d.particleSize;                          // soft blurred flecks, like the drawn ones
            float len = st.body * 0.075f * d.particleSize * (0.6f + 0.8f * ((h >>> 5) & 255) / 255f);
            float flick = (h & (1 << 29)) != 0 ? 1f : 0.55f;
            int alpha = Mth.clamp((int) (235 * Mth.sin(Mth.PI * life) * flick * st.fade), 0, 255);
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

    /** The drawing rate of an aura's main shell (0: smooth). */
    private static float mainFps(AuraDef d) {
        int i = mainShell(d);
        return i < 0 ? 0 : d.layers.get(i).fps;
    }

    private static final float[] BX = new float[8], BY = new float[8], BZ = new float[8];

    /**
     * Lightning crawling over the aura (Super Saiyan 2 and the like): jagged bolts that flash for a few frames at a
     * time somewhere on the shell, each a bright core in a wider glow. Returns how many quads it drew.
     */
    private static int lightning(PoseStack pose, VertexConsumer vc, Slot st, float time, int budget, int detail) {
        AuraDef d = st.def;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int bolts = Math.min(budget / 14, Math.round(d.lightningRate * (1 + st.charge) * (detail == 0 ? 0.5f : 1f)));
        int r = (d.lightningColor >> 16) & 255, g = (d.lightningColor >> 8) & 255, b = d.lightningColor & 255;
        int drawn = 0;
        float view = (float) Math.atan2(-st.z, -st.x);
        for (int i = 0; i < bolts; i++) {
            float rate = 9f + (i % 3) * 3f;                                       // a new bolt this many times a second
            float phase = time * rate + i * 0.53f + st.seed;
            int cycle = Mth.floor(phase);
            int h = hash((int) (st.seed * 331) + i * 2654435 + cycle * 40503);
            if ((h & 3) == 0) continue;                                           // a gap now and then: it flickers
            float life = phase - cycle;
            float r0 = (h & 1023) / 1023f, r1 = ((h >>> 10) & 1023) / 1023f, r2 = ((h >>> 20) & 1023) / 1023f;
            // inside the aura's body, short enough that no bolt reaches past its outline
            float s = 0.15f + 0.6f * r1;
            float a = view + (r0 < 0.5f ? 1 : -1) * (Mth.HALF_PI * (0.55f + 0.5f * r2)) + (r0 - 0.5f) * 0.6f;
            float room = AuraShell.profile(d, s) * st.radius;
            float rr = room * (0.3f + 0.3f * r2);
            float x = st.x + Mth.cos(a) * rr, y = st.y + st.bottom + s * st.height, z = st.z + Mth.sin(a) * rr;
            float len = Math.min(st.body * 0.45f * d.lightningSize * (0.6f + 0.8f * r0), room * 0.55f);
            float dir = (r2 - 0.5f) * 2.4f + (r1 < 0.5f ? 0 : Mth.PI);
            int pts = 6;
            for (int k = 0; k < pts; k++) {                                       // a jagged walk
                int hk = hash(h + k * 7919);
                float jx = ((hk & 255) / 255f - 0.5f) * 0.5f, jy = (((hk >>> 8) & 255) / 255f - 0.5f) * 0.5f;
                float q = k / (float) (pts - 1);
                BX[k] = x + (Mth.cos(a + Mth.HALF_PI) * Mth.cos(dir) * q + jx * 0.4f) * len;
                BY[k] = y + (Mth.sin(dir) * q + jy * 0.4f) * len;
                BZ[k] = z + (Mth.sin(a + Mth.HALF_PI) * Mth.cos(dir) * q + jx * 0.4f) * len;
            }
            int alpha = Mth.clamp((int) (255 * st.fade * (1 - life * 0.6f)), 0, 255);
            drawn += bolt(vc, m, nm, pts, st.body, r, g, b, alpha, 1f);
            // a branch forking off part way along, thinner and shorter
            int from = 1 + (h >>> 28 & 1) + 1;
            float bx = BX[from], by = BY[from], bz = BZ[from];
            float bdir = dir + ((h & 64) != 0 ? 0.9f : -0.9f);
            for (int k = 0; k < 4; k++) {
                int hk = hash(h * 31 + k * 4099);
                float jx = ((hk & 255) / 255f - 0.5f) * 0.5f, jy = (((hk >>> 8) & 255) / 255f - 0.5f) * 0.5f;
                float q = k / 3f * 0.5f;
                BX[k] = bx + (Mth.cos(a + Mth.HALF_PI) * Mth.cos(bdir) * q + jx * 0.25f) * len;
                BY[k] = by + (Mth.sin(bdir) * q + jy * 0.25f) * len;
                BZ[k] = bz + (Mth.sin(a + Mth.HALF_PI) * Mth.cos(bdir) * q + jx * 0.25f) * len;
            }
            drawn += bolt(vc, m, nm, 4, st.body, r, g, b, alpha * 3 / 4, 0.6f);
        }
        return drawn;
    }

    /** One bolt along the first {@code pts} points of BX/BY/BZ: a wide glow in its colour, then a white-hot core. */
    private static int bolt(VertexConsumer vc, Matrix4f m, Matrix3f nm, int pts, float body, int r, int g, int b, int alpha, float thick) {
        int drawn = 0;
        for (int pass = 0; pass < 2; pass++) {
            float w = body * (pass == 0 ? 0.07f : 0.02f) * thick;
            int cr = pass == 0 ? r : 255, cg = pass == 0 ? g : 255, cb = pass == 0 ? b : 255;
            int ca = pass == 0 ? alpha / 2 : alpha;
            for (int k = 0; k < pts - 1; k++) {
                float ex = BX[k] - BX[k + 1], ey = BY[k] - BY[k + 1], ez = BZ[k] - BZ[k + 1];
                // across the segment and the line of sight
                float cx2 = ey * BZ[k] - ez * BY[k], cy2 = ez * BX[k] - ex * BZ[k], cz2 = ex * BY[k] - ey * BX[k];
                float cl = Mth.sqrt(cx2 * cx2 + cy2 * cy2 + cz2 * cz2);
                if (cl < 1e-5f) continue;
                cx2 = cx2 / cl * w;
                cy2 = cy2 / cl * w;
                cz2 = cz2 / cl * w;
                corner(vc, m, nm, BX[k] - cx2, BY[k] - cy2, BZ[k] - cz2, 0, 0.5f, cr, cg, cb, ca);
                corner(vc, m, nm, BX[k] + cx2, BY[k] + cy2, BZ[k] + cz2, 1, 0.5f, cr, cg, cb, ca);
                corner(vc, m, nm, BX[k + 1] + cx2, BY[k + 1] + cy2, BZ[k + 1] + cz2, 1, 0.5f, cr, cg, cb, ca);
                corner(vc, m, nm, BX[k + 1] - cx2, BY[k + 1] - cy2, BZ[k + 1] - cz2, 0, 0.5f, cr, cg, cb, ca);
                drawn++;
            }
        }
        return drawn;
    }

    /**
     * The ground lit under a charging fighter (a ring of the aura's colour that pulses) and the ring a new aura throws
     * out as it bursts.
     */
    private static void ground(PoseStack pose, VertexConsumer vc, State s, Slot sl, float time) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int c = sl.def.edge;
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        float y = sl.y + 0.03f;
        if (sl.charge > 0.02f && !"none".equals(sl.def.ground)) {
            float size = sl.radius * (1.15f + 0.08f * Mth.sin(time * 6f + s.seed));
            flat(vc, m, nm, sl.x, y, sl.z, size, time * 0.8f, r, g, b, Mth.clamp((int) (140 * sl.charge * sl.fade), 0, 255));
        }
        float burst = Math.max(s.form.flash, s.tech.flash);
        if (burst > 0.02f) {
            float size = sl.radius * (1.2f + 3.5f * (1 - burst));
            flat(vc, m, nm, sl.x, y + 0.02f, sl.z, size, 0, r, g, b, Mth.clamp((int) (220 * burst), 0, 255));
        }
    }

    private static void flat(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x, float y, float z, float size, float turn, int r, int g, int b, int a) {
        float c = Mth.cos(turn) * size, s = Mth.sin(turn) * size;
        corner(vc, m, nm, x - c + s, y, z - s - c, 0, 0, r, g, b, a);
        corner(vc, m, nm, x - c - s, y, z - s + c, 0, 1, r, g, b, a);
        corner(vc, m, nm, x + c - s, y, z + s + c, 1, 1, r, g, b, a);
        corner(vc, m, nm, x + c + s, y, z + s - c, 1, 0, r, g, b, a);
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
                    preview = previewTech = null;
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
                .then(Commands.literal("reload").executes(ctx -> {
                    int n = reload();
                    tell(Component.literal("Reloaded " + n + " auras"));
                    return n;
                }))
                .then(Commands.argument("aura", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(AuraDefs.ids(), b))
                        .executes(ctx -> wear(StringArgumentType.getString(ctx, "aura"), -1))
                        .then(Commands.argument("stage", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 255))
                                .executes(ctx -> wear(StringArgumentType.getString(ctx, "aura"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "stage"))))));
    }

    /** Whether /dbzaura set and dump work on the technique you preview rather than the form's aura. */
    private static boolean focusTech;

    /** /dbzaura <id> [stage]: a form's aura replaces yours; a technique's (Kaioken) goes over it, at that stage. */
    private static int wear(String id, int stage) {
        AuraDef d = AuraDefs.byId(id);
        if (d == null) {
            tell(Component.translatable("message.dbzenith.aura_unknown", id));
            return 0;
        }
        if (d.technique != null) {
            previewTech = d;
            previewStage = stage > 0 ? stage : 2;
            focusTech = true;
        } else {
            preview = d;
            focusTech = false;
        }
        tell(Component.translatable("message.dbzenith.aura_preview", id));
        return 1;
    }

    /** The aura you are looking at: the one you preview, else the one your form wears. */
    private static AuraDef current() {
        if (focusTech && previewTech != null) return previewTech;
        if (preview != null) return preview;
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : wanted(ClientPublicStates.get(mc.player.getId()));
    }

    /** /dbzaura set layers.1.spikes.size 0.2: changes one value of the aura you look at, live, until F3+T. */
    private static int tweak(String path, String value) {
        AuraDef d = current();
        if (d == null) {
            DBZenith.LOGGER.warn("[aura] no aura to tweak ({})", path);
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
            if (previewTech == d) previewTech = changed;
            tell(Component.literal(d.id + ": " + path + " = " + value));
            DBZenith.LOGGER.info("[aura] {}: {} = {}", d.id, path, value);
            return 1;
        } catch (Exception ex) {
            tell(Component.literal("Can't set " + path + ": " + ex.getMessage()));
            DBZenith.LOGGER.warn("[aura] can't set {} on {}: {}", path, d.id, ex.toString());
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

    /** Reads the aura files again; whoever wears one takes the new version straight away. */
    static int reload() {
        int n = AuraDefs.reloadNow();
        if (preview != null) preview = AuraDefs.byId(preview.id);
        if (previewTech != null) previewTech = AuraDefs.byId(previewTech.id);
        return n;
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
        if (name.contains("aurareload")) reload();
        int at = name.indexOf("aurapv.");
        if (at >= 0) {
            String[] parts = name.substring(at + 7).split("\\.");
            devPreview(parts.length > 0 ? parts[0] : "off");
            devState = parts.length > 1 && !parts[1].isEmpty() ? parts[1].replaceAll("_.*", "") : "idle";
            focusTech = false;
        }
        int tech = name.indexOf("auratech.");
        if (tech >= 0) {
            String[] parts = name.substring(tech + 9).split("\\.");
            AuraDef d = parts.length > 0 ? AuraDefs.byId(parts[0]) : null;
            previewTech = d != null && d.technique != null ? d : null;
            previewStage = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("\\D.*", "")) : 2;
            focusTech = previewTech != null;
        } else if (at >= 0) {
            previewTech = null;
        }
        for (int set = name.indexOf("auraset."); set >= 0; set = name.indexOf("auraset.", set + 8)) {
            String spec = name.substring(set + 8).split("_")[0];
            int eq = spec.indexOf('+');
            if (eq > 0) tweak(spec.substring(0, eq).replace('-', '.'), spec.substring(eq + 1));
        }
    }
}
