package com.dbzenith.client.motion;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.PublicStatePacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;

/**
 * The motion engine (CX-18 Animation v5): one state machine, one set of procedural layers, for every humanoid figure,
 * the player and every NPC alike. Entities hold no animation code; the engine reads what they are doing (speed, ground,
 * air, water, flight, sprint, charge, hurt, death, items, swings), picks a {@link State} with a little hysteresis,
 * cross-fades clips from the entity's {@link AnimSet}, keeps gait cycles locked to the ground covered, and layers the
 * procedural motion on top. {@link #sample} turns that into a {@link com.dbzenith.client.motion.Pose} each frame.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class MotionEngine {
    private static final Int2ObjectOpenHashMap<Motion> MOTIONS = new Int2ObjectOpenHashMap<>();
    private static net.minecraft.world.level.Level lastLevel;
    /** Whole-figure pivot heights (blocks above the feet): the feet on the ground, the middle in the air. */
    private static final Bone[] ARMS = {Bone.RIGHT_ARM, Bone.LEFT_ARM};
    private static final float PIVOT_GROUND = 0f, PIVOT_AIR = 0.9f, ANIMATOR_PIVOT = 0.7f;

    private MotionEngine() {}

    public static boolean enabled() {
        return DBZConfig.CLIENT.animationEngine.get();
    }

    /** This entity's motion, created on first use. */
    public static Motion get(LivingEntity e) {
        Motion m = MOTIONS.get(e.getId());
        if (m == null || m.entity != e) {
            m = new Motion(e);
            MOTIONS.put(e.getId(), m);
        }
        return m;
    }

    public static Motion peek(LivingEntity e) {
        Motion m = MOTIONS.get(e.getId());
        return m != null && m.entity == e ? m : null;
    }

    public static int count() {
        return MOTIONS.size();
    }

    /**
     * Tips and moves the whole figure for a non-player (players get this from playerAnimator): about 0.7 blocks above the
     * feet, scaled with the renderer's size.
     */
    public static void applyBody(LivingEntity e, com.mojang.blaze3d.vertex.PoseStack pose, float pt, float scale) {
        dev.kosmx.playerAnim.api.layered.IAnimation a = com.dbzenith.client.anim.NpcActions.bodySource(e, MotionAnimation.of(get(e)));   // the combat layers move the body too (CX-19e)
        a.setupAnim(pt);
        if (!a.isActive()) return;
        dev.kosmx.playerAnim.core.util.Vec3f pos = a.get3DTransform("body", dev.kosmx.playerAnim.api.TransformType.POSITION, pt, dev.kosmx.playerAnim.core.util.Vec3f.ZERO);
        dev.kosmx.playerAnim.core.util.Vec3f rot = a.get3DTransform("body", dev.kosmx.playerAnim.api.TransformType.ROTATION, pt, dev.kosmx.playerAnim.core.util.Vec3f.ZERO);
        pose.translate(pos.getX() * scale, (pos.getY() + ANIMATOR_PIVOT) * scale, pos.getZ() * scale);
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(rot.getZ()));
        pose.mulPose(com.mojang.math.Axis.YP.rotation(rot.getY()));
        pose.mulPose(com.mojang.math.Axis.XP.rotation(rot.getX()));
        pose.translate(0, -ANIMATOR_PIVOT * scale, 0);
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            MOTIONS.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        for (Player p : mc.level.players()) get(p);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Iterator<Motion> it = MOTIONS.values().iterator();
        while (it.hasNext()) {
            Motion m = it.next();
            LivingEntity e = m.entity;
            if (e.isRemoved() || e.level() != mc.level || (!(e instanceof Player) && now - m.lastSeen > 60)) {
                it.remove();
                continue;
            }
            double d2 = e.distanceToSqr(cam);
            m.simple = d2 > Tuning.lodSimple * Tuning.lodSimple;
            boolean off = d2 > Tuning.lodOff * Tuning.lodOff;
            if (m.simple && !off && (now + e.getId()) % 2 == 1 && m.lastTick == now - 1) continue;   // far: half rate
            tick(m, now, off);
        }
    }

    static void tick(Motion m, long now, boolean off) {
        LivingEntity e = m.entity;
        float dt = m.lastTick > 0 ? Math.min(3, now - m.lastTick) : 1;
        m.lastTick = now;
        System.arraycopy(m.weight, 0, m.weightO, 0, State.COUNT);
        System.arraycopy(m.time, 0, m.timeO, 0, State.COUNT);
        System.arraycopy(m.mask, 0, m.maskO, 0, Bone.COUNT);
        m.gaitO = m.gait;
        m.engineO = m.engine;
        m.leanO = m.lean;
        m.rollO = m.roll;
        m.flyPitchO = m.flyPitch;
        m.bankO = m.bank;
        m.armLagO = m.armLag;
        m.landO = m.land;
        m.takeoffO = m.takeoff;
        m.flyBlendO = m.flyBlend;
        m.walkAmpO = m.walkAmp;
        m.fightO = m.fight;
        resolveClips(m);
        m.fight = approach(m.fight, fighting(e) ? 1f : 0f, dt / Math.max(1f, Tuning.blendFight));   // into the fighting set and out, blended
        sense(m);

        // ---- the engine against vanilla: off for swimming, riding, sleeping, crouching, dying, the Great Ape
        PublicStatePacket ps = e instanceof Player ? ClientPublicStates.get(e.getId()) : null;
        boolean vanilla = off || !enabled() || e.isPassenger() || e.isSleeping() || e.isFallFlying() || e.isSwimming()
                || e.getPose() == net.minecraft.world.entity.Pose.SWIMMING || e.isCrouching() || e.deathTime > 0 || e.isAutoSpinAttack()
                || (e.isInWater() && !m.flying) || (ps != null && ps.form() != null && (ps.form().contains("great_ape") || ps.form().equals("golden_ape")));
        m.engine = approach(m.engine, vanilla ? 0f : 1f, dt / Math.max(1f, Tuning.blendEngine));

        // ---- choose the state
        State want = choose(m, ps);
        if (want != m.state) {
            if (want != m.candidate) {
                m.candidate = want;
                m.candidateTicks = 0;
            }
            boolean urgent = want.kind != m.state.kind;                        // ground to air and back cannot wait
            if (urgent || ++m.candidateTicks >= Tuning.stateHold) {
                if (want.flying() && !m.state.flying() && m.airTicks < 6) m.takeoff = 1f;   // springing off the ground into flight
                m.state = want;
                m.time[want.ordinal()] = want.kind == State.Kind.AIR ? 0 : m.time[want.ordinal()];
            }
        } else {
            m.candidate = want;
        }
        for (State s : State.ALL) {
            int i = s.ordinal();
            float target = s == m.state ? 1f : 0f;
            m.weight[i] = approach(m.weight[i], target, dt / Math.max(1f, (target > 0 ? s : m.state).blend()));
            if (m.weight[i] > 0f || s == m.state) {
                Clip c = m.clips[i];
                m.time[i] += dt;
                if (c != null && c.loop && m.time[i] > c.length * 1000) m.time[i] -= c.length * 1000;   // keep floats small
            }
        }

        // ---- the gait phase follows the ground covered
        Clip walk = m.clips[State.WALK.ordinal()], sprint = m.clips[State.SPRINT.ordinal()];
        float ww = m.weight[State.WALK.ordinal()], sw = m.weight[State.SPRINT.ordinal()];
        m.walkAmp = (float) Mth.clamp(m.speed / Tuning.walkSpeed, 0.3, 1.15);
        if (ww + sw > 0.001f && m.ground) {
            float strideWalk = walk == null ? 1.4f : stride(walk, m.walkAmp) * m.sizeScale;
            float strideSprint = sprint == null ? 2.2f : sprint.naturalStride() * m.sizeScale * (float) Mth.clamp(m.speed / Tuning.sprintSpeed, 0.85, 1.35);
            float stride = (strideWalk * ww + strideSprint * sw) / (ww + sw);
            m.gait += (float) (m.speed / Math.max(0.2f, stride)) * dt;
        } else if (m.speed < Tuning.moveThreshold) {
            float frac = m.gait - (float) Math.floor(m.gait);                   // settle the feet together
            float target = frac < 0.25f ? 0f : frac < 0.75f ? 0.5f : 1f;
            m.gait += (target - frac) * 0.15f;
        }

        // ---- masks: arms that hold or use something, or swing at someone, go back to vanilla
        boolean using = e.isUsingItem();
        boolean rightHanded = e.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        boolean mainBusy = !e.getMainHandItem().isEmpty() || (e.swinging && !(e instanceof Player));
        boolean offBusy = !e.getOffhandItem().isEmpty();
        float mainArm = using || mainBusy ? 0f : 1f, offArm = using || offBusy ? 0f : 1f;
        approachMask(m, Bone.RIGHT_ARM, rightHanded ? mainArm : offArm, dt);
        approachMask(m, Bone.LEFT_ARM, rightHanded ? offArm : mainArm, dt);

        // ---- procedural layers
        float k = Mth.clamp(Tuning.smoothing * dt, 0, 1);
        boolean air = m.state.flying();
        m.flyBlend = approach(m.flyBlend, air ? 1f : 0f, dt / Math.max(1f, Tuning.blendFly));
        if (m.simple) {
            m.lean += (0 - m.lean) * k;
            m.roll += (0 - m.roll) * k;
            m.bank += (0 - m.bank) * k;
            m.armLag += (0 - m.armLag) * k;
        } else {
            float leanTarget = 0, rollTarget = 0, pitchTarget = 0, bankTarget = 0;
            if (m.ground && !air) {
                double over = Math.max(0, m.speed - Tuning.walkSpeed * 0.6);
                leanTarget = (float) Mth.clamp(over * Tuning.runLean + (m.speed - m.speedPrev) * 70, -6, Tuning.runLeanMax);
                if (m.forward < -0.02) leanTarget = (float) Mth.clamp(m.forward * 25, -6, 0);         // backing up: lean back
                rollTarget = (float) Mth.clamp(-m.yawRate * Tuning.turnLean * Math.min(1, m.speed / Tuning.walkSpeed), -Tuning.turnLeanMax, Tuning.turnLeanMax);
            }
            if (air) {
                double h = Math.sqrt(m.forward * m.forward + m.lateral * m.lateral);
                float base;
                if (m.forward < -0.04 && -m.forward > Math.abs(m.lateral)) base = (float) Mth.clamp(m.forward * 60, -22, 0);   // backing off: lean back
                else if (h <= Tuning.flyCruise) base = (float) (h / Tuning.flyCruise) * 18f;
                else if (h <= Tuning.flyFast) base = 18f + (float) ((h - Tuning.flyCruise) / (Tuning.flyFast - Tuning.flyCruise)) * (Tuning.flyPitchCruise - 18f);
                else base = Tuning.flyPitchCruise + (float) Math.min(1, (h - Tuning.flyFast) / Tuning.flyFast) * (Tuning.flyPitchMax - Tuning.flyPitchCruise);
                float climb = (float) (-m.vy * Tuning.flyClimb * Math.min(1, h / Tuning.flyCruise));
                pitchTarget = Mth.clamp(base + climb, -30, 105);
                bankTarget = (float) Mth.clamp(-m.yawRate * Tuning.flyBank * Math.min(1, h / 0.5) - m.lateral * Tuning.strafeBank,
                        -Tuning.flyBankMax, Tuning.flyBankMax);
            }
            m.lean += (leanTarget - m.lean) * k;
            m.roll += (rollTarget - m.roll) * k;
            m.flyPitch += (pitchTarget - m.flyPitch) * k * 0.7f;
            m.bank += (bankTarget - m.bank) * k * 0.8f;
            float lagTarget = (float) Mth.clamp(m.yawRate * 0.9f + (m.speedPrev - m.speed) * 90, -22, 22) * Tuning.armLag;
            m.armLag += (lagTarget - m.armLag) * 0.3f * dt;
        }
        m.land = Math.max(0, m.land - 0.11f * dt);
        m.takeoff = Math.max(0, m.takeoff - 0.12f * dt);
        if (m.state == State.SPRINT) m.fatigue = Math.min(1, m.fatigue + 0.004f * dt);
        else m.fatigue = Math.max(0, m.fatigue - 0.0025f * dt);
        if (e instanceof net.minecraft.client.player.AbstractClientPlayer p && m.state == State.FAST && m.engine > 0.5f) {
            com.dbzenith.client.fx.Afterimages.keepAlive(p, 3);
        }
    }

    private static void approachMask(Motion m, Bone b, float target, float dt) {
        int i = b.ordinal();
        m.mask[i] = approach(m.mask[i], target, dt / 3f);
    }

    static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    /** Stride of a gait clip at a swing amplitude (slow walks take shorter steps). */
    private static float stride(Clip c, float amp) {
        if (c.stride > 0) return c.stride * amp;
        float half = (float) Math.toRadians(c.legSwing() * amp / 2f);
        return Math.max(0.3f, 4f * Clip.LEG * (float) Math.sin(half));
    }

    /** Reads the entity: speeds in its own frame, turn rate, ground and air time, flight, landings. */
    private static void sense(Motion m) {
        LivingEntity e = m.entity;
        double dx = e.getX() - e.xo, dy = e.getY() - e.yo, dz = e.getZ() - e.zo;
        m.speedPrev = m.speed;
        double vyPrev = m.vy;
        m.speed = Math.sqrt(dx * dx + dz * dz);
        m.vy = dy;
        float yawDeg = e instanceof Player ? e.getYRot() : e.yBodyRot;              // players: where they look is where they mean to go
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        m.forward = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw);
        m.lateral = dx * -Mth.cos(yaw) + dz * -Mth.sin(yaw);
        m.yawRate = e instanceof Player ? Mth.wrapDegrees(e.getYRot() - e.yRotO) : Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO);
        m.sizeScale = Math.max(0.3f, e.getBbHeight() / 1.8f);
        boolean ground = e.onGround() || (e instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi() && !e.isNoGravity());   // a frozen mob never updates its footing
        PublicStatePacket ps = e instanceof Player ? ClientPublicStates.get(e.getId()) : null;
        boolean flag = (ps != null && ps.has(PublicStatePacket.FLYING))
                || (e instanceof Player p && p.getAbilities().flying)
                || (e instanceof com.dbzenith.npc.Animated a && a.isFlyingNow());
        // a body that hangs in the air without gravity pulling it down is flying, whatever it is
        boolean hanging = !ground && m.airTicks > 4 && Math.abs(m.vy - vyPrev) < 0.025 && !e.isInWater();
        m.wasFlying = m.flying;
        m.flying = !ground && (flag || hanging || (e.isNoGravity() && !(e instanceof Player)));
        if (ground) {
            if (m.airTicks > 2) {                                                  // touchdown
                double impact = Math.max(m.fallSpeed, m.wasFlying ? 0.3 : 0);
                if (impact > Tuning.landImpact * 0.6) m.land = (float) Mth.clamp((impact - Tuning.landImpact * 0.45) * 1.6, 0.25, 1) * Tuning.landCrouch;
            }
            m.groundTicks++;
            m.airTicks = 0;
            m.fallSpeed = 0;
        } else {
            m.airTicks++;
            m.groundTicks = 0;
            if (!m.flying) m.fallSpeed = Math.max(m.fallSpeed, -dy);
        }
        m.ground = ground;
    }

    private static State choose(Motion m, PublicStatePacket ps) {
        LivingEntity e = m.entity;
        if (e instanceof com.dbzenith.npc.Animated a && a.isChargingKi() && m.clips[State.CHARGE.ordinal()] != null) return State.CHARGE;
        if (m.flying) {
            double h = Math.sqrt(m.forward * m.forward + m.lateral * m.lateral);
            if (m.vy > Tuning.flyVertical && h < 0.35) return State.ASCEND;
            if (m.vy < -Tuning.flyVertical && h < 0.35) return State.DESCEND;
            if (m.forward < -0.06 && -m.forward > Math.abs(m.lateral) * 0.8 && h > Tuning.flyCruise * 0.6) return State.BACKWARD;
            if (h > Tuning.flyFast) return State.FAST;
            if (h > Tuning.flyCruise) return State.CRUISE;
            return State.HOVER;
        }
        if (!m.ground && !e.isInWater()) {
            if (m.airTicks < 2 && m.state.kind == State.Kind.GAIT) return m.state;      // stepping off a block
            return m.vy > 0.02 ? State.JUMP : State.FALL;
        }
        if (m.speed < Tuning.moveThreshold) return State.IDLE;
        boolean sprinting = e.isSprinting() || (!(e instanceof Player) && m.speed > Tuning.sprintSpeed);
        if (sprinting && m.speed > Tuning.sprintSpeed * 0.7) return State.SPRINT;
        return State.WALK;
    }


    /**
     * Whether a figure is in the fighting set (CX-20): a player in PvP mode; a training dummy that isn't just standing;
     * any other mob while it is aggressive (in a fight).
     */
    static boolean fighting(LivingEntity e) {
        if (e instanceof Player) {
            PublicStatePacket ps = ClientPublicStates.get(e.getId());
            return ps != null && ps.has(PublicStatePacket.PVP);
        }
        if (e instanceof com.dbzenith.npc.TrainingDummy d) return d.mode() != com.dbzenith.npc.TrainingDummy.Mode.STAND;
        return e instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive();
    }
    /** Looks each state's clip up again when the data reloads or the figure's race or form changes. */
    private static void resolveClips(Motion m) {
        LivingEntity e = m.entity;
        MotionData.Profile profile = MotionData.profileFor(e);
        String race = profile.race(), form = profile.form();
        if (e instanceof Player) {
            PublicStatePacket ps = ClientPublicStates.get(e.getId());
            if (ps != null) {
                if (ps.raceEnum() != null) race = ps.raceEnum().name().toLowerCase(java.util.Locale.ROOT);
                form = ps.form();
            }
        }
        String styleKey = com.dbzenith.client.ClientStyles.key(e);                  // fighting styles (CX-20)
        if (m.clipsVersion == MotionData.version() && java.util.Objects.equals(race, m.raceKey) && java.util.Objects.equals(form, m.formKey)
                && styleKey.equals(m.styleKey)) return;
        m.styleKey = styleKey;
        m.clipsVersion = MotionData.version();
        m.raceKey = race;
        m.formKey = form;
        net.minecraft.resources.ResourceLocation type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        String[] keys = {type == null ? null : "entity:" + type, form == null ? null : "form:" + form, race == null ? null : "race:" + race};
        AnimSet set = MotionData.sets().get(profile.set());
        for (State s : State.ALL) {
            String id = styled(e, s, false, set == null ? null : set.clipFor(s, keys, MotionData.sets(), 0));
            m.clips[s.ordinal()] = MotionData.clip(id);
        }
        String[] fightKeys = {"mode:fighting", keys[0], keys[1], keys[2]};   // the fighting stance wins over a race's or form's idle
        for (State s : State.ALL) {
            String id = styled(e, s, true, set == null ? null : set.clipFor(s, fightKeys, MotionData.sets(), 0));
            Clip c = MotionData.clip(id);
            m.fightClips[s.ordinal()] = c == m.clips[s.ordinal()] ? null : c;   // null: the same clip either way
        }
    }

    /**
     * A style's clip for this state, if the figure's style fills that slot (and the clip exists), else {@code fallback}.
     * In the fighting set, states without a fighting slot of their own (sprint, flight...) keep the ordinary slot's.
     */
    private static String styled(LivingEntity e, State s, boolean fighting, String fallback) {
        boolean ownSlot = false;
        for (com.dbzenith.style.StyleSlot slot : com.dbzenith.style.StyleSlot.ALL) {
            if (slot.fighting != fighting || !slot.state.equals(s.key)) continue;
            ownSlot = true;
            String clip = com.dbzenith.client.ClientStyles.clip(e, slot);
            if (clip != null && MotionData.clip(clip) != null) return clip;
        }
        return fighting && !ownSlot ? styled(e, s, false, fallback) : fallback;
    }

    // ------------------------------------------------------------------ sampling

    /**
     * Fills {@code m.pose} for this frame: the clips cross-faded, the procedural layers on top, and the rig coupled (the
     * arms and head ride on the torso's lean and twist). Values in degrees and pixels; the whole figure's offset ("body"
     * position) already includes the pivot correction, in blocks (y up).
     */
    public static Pose sample(Motion m, float pt) {
        long frame = m.entity.level().getGameTime();
        if (m.sampledFrame == frame && m.sampledAt == pt) return m.pose;
        m.sampledFrame = frame;
        m.sampledAt = pt;
        m.lastSeen = m.entity.level().getGameTime();
        Pose p = m.pose;
        p.zero();
        float total = 0f;
        for (int i = 0; i < State.COUNT; i++) total += smooth(Mth.lerp(pt, m.weightO[i], m.weight[i]));
        if (total < 1e-4f) total = 1f;
        float gait = Mth.lerp(pt, m.gaitO, m.gait);
        float amp = Mth.lerp(pt, m.walkAmpO, m.walkAmp);
        for (State s : State.ALL) {
            int i = s.ordinal();
            float w = smooth(Mth.lerp(pt, m.weightO[i], m.weight[i])) / total;
            Clip c = m.clips[i], fc = m.fightClips[i];
            if (w <= 0f || c == null) continue;
            if (s == State.WALK) w *= amp;                                    // slow walks swing less
            float f = fc == null ? 0f : smooth(Mth.lerp(pt, m.fightO, m.fight));
            if (f < 1f) c.sample(c.sync == Clip.Sync.STRIDE ? gait : Mth.lerp(pt, m.timeO[i], m.time[i]) / c.length, p, w * (1f - f));
            if (f > 0f) fc.sample(fc.sync == Clip.Sync.STRIDE ? gait : Mth.lerp(pt, m.timeO[i], m.time[i]) / fc.length, p, w * f);   // the fighting stance (CX-20)
        }
        if (!m.simple) procedural(m, p, pt);
        couple(m, p, pt);
        return p;
    }

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }

    private static void procedural(Motion m, Pose p, float pt) {
        float lean = Mth.lerp(pt, m.leanO, m.lean), roll = Mth.lerp(pt, m.rollO, m.roll);
        float fly = Mth.lerp(pt, m.flyBlendO, m.flyBlend);
        float pitch = Mth.lerp(pt, m.flyPitchO, m.flyPitch) * fly, bank = Mth.lerp(pt, m.bankO, m.bank) * fly;
        float land = Mth.lerp(pt, m.landO, m.land), takeoff = Mth.lerp(pt, m.takeoffO, m.takeoff);
        float lag = Mth.lerp(pt, m.armLagO, m.armLag);
        // a run leans from the waist (most of it) and from the feet
        p.add(Bone.TORSO, Bone.PITCH, lean * 0.6f);
        p.add(Bone.BODY, Bone.PITCH, lean * 0.4f + pitch);
        p.add(Bone.BODY, Bone.ROLL, roll + bank);
        p.add(Bone.HEAD, Bone.ROLL, -(roll + bank) * 0.45f);
        // the arms trail sudden turns and stops
        p.add(Bone.RIGHT_ARM, Bone.ROLL, lag * 0.6f + Math.max(0, lag) * 0.4f);
        p.add(Bone.LEFT_ARM, Bone.ROLL, lag * 0.6f + Math.min(0, lag) * 0.4f);
        p.add(Bone.RIGHT_ARM, Bone.PITCH, Math.abs(lag) * 0.5f);
        p.add(Bone.LEFT_ARM, Bone.PITCH, Math.abs(lag) * 0.5f);
        // breathing: deeper after a sprint, only while standing
        float idle = Mth.lerp(pt, m.weightO[State.IDLE.ordinal()], m.weight[State.IDLE.ordinal()]);
        if (idle > 0f && m.fatigue > 0.02f) {
            float t = (m.entity.tickCount + pt) * (0.16f + m.fatigue * 0.12f);
            float b = Mth.sin(t) * m.fatigue * 2.4f * Tuning.breathe * idle;
            p.add(Bone.TORSO, Bone.PITCH, 2.5f * m.fatigue * idle + b);
            p.add(Bone.HEAD, Bone.PITCH, b * 0.6f);
            p.add(Bone.RIGHT_ARM, Bone.ROLL, b * 0.5f);
            p.add(Bone.LEFT_ARM, Bone.ROLL, -b * 0.5f);
        }
        // the landing crouch: knees give, hips drop, arms out for balance
        if (land > 0.001f) {
            float l = land * land * (3 - 2 * land);
            p.add(Bone.BODY, Bone.Y, 3.4f * l);
            p.add(Bone.RIGHT_LEG, Bone.PITCH, -26f * l);
            p.add(Bone.LEFT_LEG, Bone.PITCH, -18f * l);
            p.add(Bone.RIGHT_LEG, Bone.BEND, 58f * l);
            p.add(Bone.LEFT_LEG, Bone.BEND, 50f * l);
            p.add(Bone.TORSO, Bone.PITCH, 14f * l);
            p.add(Bone.RIGHT_ARM, Bone.ROLL, 16f * l);
            p.add(Bone.LEFT_ARM, Bone.ROLL, -16f * l);
            p.add(Bone.RIGHT_ARM, Bone.PITCH, -18f * l);
            p.add(Bone.LEFT_ARM, Bone.PITCH, -18f * l);
        }
        // the takeoff: a quick crouch, then a spring up with the arms swept down
        if (takeoff > 0.001f) {
            float u = 1f - takeoff;                                            // 0 at lift-off .. 1 done
            float crouch = u < 0.35f ? Mth.sin(u / 0.35f * Mth.PI) : 0f;
            float spring = u >= 0.25f ? Mth.sin((u - 0.25f) / 0.75f * Mth.PI) : 0f;
            p.add(Bone.BODY, Bone.Y, 2.4f * crouch - 1.2f * spring);
            p.add(Bone.RIGHT_LEG, Bone.BEND, 46f * crouch);
            p.add(Bone.LEFT_LEG, Bone.BEND, 46f * crouch);
            p.add(Bone.RIGHT_LEG, Bone.PITCH, -20f * crouch + 8f * spring);
            p.add(Bone.LEFT_LEG, Bone.PITCH, -20f * crouch + 8f * spring);
            p.add(Bone.RIGHT_ARM, Bone.PITCH, 30f * spring - 20f * crouch);
            p.add(Bone.LEFT_ARM, Bone.PITCH, 30f * spring - 20f * crouch);
            p.add(Bone.RIGHT_ARM, Bone.ROLL, 12f * spring);
            p.add(Bone.LEFT_ARM, Bone.ROLL, -12f * spring);
            p.add(Bone.TORSO, Bone.PITCH, 10f * crouch);
        }
    }

    /**
     * Couples the rig: the torso leans and twists about the waist, carrying the shoulders and the neck with it; the head
     * counters the figure's pitch so the eyes stay ahead; the whole figure turns about its feet on the ground and its
     * middle in the air.
     */
    private static void couple(Motion m, Pose p, float pt) {
        float lean = p.get(Bone.TORSO, Bone.PITCH), twist = p.get(Bone.TORSO, Bone.YAW);
        float lr = lean * Mth.DEG_TO_RAD, tr = twist * Mth.DEG_TO_RAD;
        float neckY = 12f - 12f * Mth.cos(lr), neckZ = -12f * Mth.sin(lr);         // the neck swings forward and down
        float shY = 12f - 10f * Mth.cos(lr) - 2f, shZ = -10f * Mth.sin(lr);
        p.add(Bone.TORSO, Bone.Y, neckY);
        p.add(Bone.TORSO, Bone.Z, neckZ);
        p.add(Bone.HEAD, Bone.Y, neckY);
        p.add(Bone.HEAD, Bone.Z, neckZ);
        for (Bone arm : ARMS) {
            float x0 = arm == Bone.RIGHT_ARM ? -5f : 5f;
            p.add(arm, Bone.Y, shY);
            p.add(arm, Bone.Z, shZ + x0 * Mth.sin(tr));
            p.add(arm, Bone.X, x0 * (Mth.cos(tr) - 1f));
            p.add(arm, Bone.PITCH, lean);
            p.add(arm, Bone.YAW, twist);
        }
        // the head keeps looking where it looks: it takes back the body's and the torso's pitch
        float bodyPitch = p.get(Bone.BODY, Bone.PITCH);
        p.add(Bone.HEAD, Bone.PITCH, -(bodyPitch + lean) * Tuning.headCounter);
        p.add(Bone.HEAD, Bone.YAW, -twist * 0.8f);
        // the whole figure: pixels (y down) to playerAnimator's blocks (y up), turned about the right pivot
        float fly = Mth.lerp(pt, m.flyBlendO, m.flyBlend);
        float pivot = Mth.lerp(fly, PIVOT_GROUND, PIVOT_AIR) - ANIMATOR_PIVOT;
        float a = bodyPitch * Mth.DEG_TO_RAD, r = p.get(Bone.BODY, Bone.ROLL) * Mth.DEG_TO_RAD;
        float bx = -p.get(Bone.BODY, Bone.X) / 16f + pivot * Mth.sin(r);              // model x points to the figure's left, the renderer's to its right
        float by = -p.get(Bone.BODY, Bone.Y) / 16f + pivot * (1f - Mth.cos(a)) + pivot * (1f - Mth.cos(r));
        float bz = p.get(Bone.BODY, Bone.Z) / 16f + pivot * Mth.sin(a);
        p.set(Bone.BODY, Bone.X, bx);
        p.set(Bone.BODY, Bone.Y, by);
        p.set(Bone.BODY, Bone.Z, bz);
        // in the renderer's frame a positive pitch tips the figure backwards: hand it over as a forward lean
        p.set(Bone.BODY, Bone.PITCH, -bodyPitch);
    }
}
