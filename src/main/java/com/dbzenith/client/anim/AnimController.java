package com.dbzenith.client.anim;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.AnimEventPacket;
import com.dbzenith.network.PublicStatePacket;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Drives every visible player's body animation from what this client already knows about them: the public state
 * (charging, guarding, flying, meditating, form), how they move, swings and hits. Two layers per player: a looping
 * stance underneath and one-shot actions on top, so a punch thrown mid-flight keeps the flying legs.
 * Technique casts and dashes come from the server as {@link AnimEventPacket}s.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class AnimController {
    public static final ResourceLocation STATE_LAYER = new ResourceLocation(DBZenith.MOD_ID, "state");
    public static final ResourceLocation ACTION_LAYER = new ResourceLocation(DBZenith.MOD_ID, "action");
    /** The motion engine (CX-18): walking, running, flight and idles, under the stances and actions. */
    public static final ResourceLocation MOTION_LAYER = new ResourceLocation(DBZenith.MOD_ID, "motion");

    private static final Map<AbstractClientPlayer, Track> TRACKS = new WeakHashMap<>();
    /** A stance must hold this many ticks before the loop swaps, so brief flickers in speed do not jitter the pose. */
    private static final int STATE_SETTLE = 3;
    private static final Map<AbstractClientPlayer, SpeedModifier[]> SPEEDS = new WeakHashMap<>();
    /** Each layer can be mirrored: with the right arm gone, everything is done with the left (the limbs rework). */
    private static final Map<AbstractClientPlayer, dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier[]> MIRRORS = new WeakHashMap<>();

    private AnimController() {}

    /** Called once from client setup. */
    public static void registerLayers() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(MOTION_LAYER, 900,
                player -> com.dbzenith.client.motion.MotionAnimation.of(com.dbzenith.client.motion.MotionEngine.get(player)));
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(STATE_LAYER, 1000, player -> withSpeed(player, 0));
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ACTION_LAYER, 1500, player -> withSpeed(player, 1));
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(new ResourceLocation(DBZenith.MOD_ID, "injury"), 2000, InjuryLayer::new);   // broken limbs (CX-53)
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(new ResourceLocation(DBZenith.MOD_ID, "rig"), 9000,   // keeps the upper body on the hips
                player -> new RigFix(() -> dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAnimLayer(player)));
    }

    /** Each layer carries a speed control, so a landed blow can freeze both fighters for a few frames (hitstop). */
    private static ModifierLayer<IAnimation> withSpeed(AbstractClientPlayer player, int slot) {
        SpeedModifier speed = new SpeedModifier(1f);
        SPEEDS.computeIfAbsent(player, p -> new SpeedModifier[2])[slot] = speed;
        var mirror = new dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier(false);
        MIRRORS.computeIfAbsent(player, p -> new dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier[2])[slot] = mirror;
        return new ModifierLayer<>(null, speed, mirror);
    }

    private static final class Track {
        KeyframeAnimation loop;        // stance now playing
        KeyframeAnimation candidate;   // stance asking to replace it
        int candidateTicks;
        boolean wasSwinging;
        boolean wasHeavy;
        long lastEventTick = Long.MIN_VALUE / 2;
        long lastHurtTick = Long.MIN_VALUE / 2;
        boolean wasDowned;
        int lastHurtTime;
        String form;
        PublicStatePacket previousState;
        int combo;
        long lastPunch;
        long actionLockUntil;          // a transformation is not interrupted by small actions
        long suppressSwingUntil;       // the swing that releases a heavy strike is part of the heavy animation
        long hitstopUntil;             // animations frozen until then
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        for (AbstractClientPlayer player : mc.level.players()) {
            Track t = TRACKS.computeIfAbsent(player, p -> new Track());
            PublicStatePacket state = ClientPublicStates.get(player.getId());
            tickHitstop(player, t, now);
            tickMirror(player);
            tickStance(player, t, state, now);
            tickActions(player, t, state, now);
        }
    }

    // ------------------------------------------------------------------ stance loops

    private static void tickStance(AbstractClientPlayer player, Track t, PublicStatePacket state, long now) {
        KeyframeAnimation want = chooseStance(player, state, t, now);
        if (want == t.loop) {
            t.candidate = null;
            return;
        }
        if (want != t.candidate) {
            t.candidate = want;
            t.candidateTicks = 0;
        }
        if (++t.candidateTicks < STATE_SETTLE && t.loop != null && want != null) return;
        ModifierLayer<IAnimation> layer = layer(player, STATE_LAYER);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE),
                want == null ? null : new KeyframeAnimationPlayer(Anims.custom(want)));
        t.loop = want;
        t.candidate = null;
    }

    /** Crawling: a leg gone (or both useless), on the ground, not flying. */
    public static boolean crawling(net.minecraft.world.entity.LivingEntity e, PublicStatePacket state) {
        return com.dbzenith.combat.Limbs.of(e).mustCrawl() && (state == null || !state.has(PublicStatePacket.FLYING)) && !e.isPassenger();
    }

    private static KeyframeAnimation chooseStance(AbstractClientPlayer player, PublicStatePacket state, Track t, long now) {
        var knocked = com.dbzenith.client.ClientKnockouts.get(player.getId());   // knocked out (CX-51): carried, crawling or lying there
        if (knocked != null) {
            if (com.dbzenith.client.ClientCarry.isCarried(player.getId())) return Anims.custom("carried", Anims.CARRIED);
            double moved = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
            if (knocked.crawl() && moved > 0.005) return Anims.custom("crawl", Anims.CRAWL);
            return Anims.custom("downed", Anims.DOWNED);
        }
        if (crawling(player, state)) {                                         // a leg gone: on the belly (the knocked-out rework)
            double moved = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
            return moved > 0.005 ? Anims.custom("crawl", Anims.CRAWL) : Anims.custom("crawl_idle", Anims.CRAWL_IDLE);
        }
        if (com.dbzenith.client.ClientCarry.isCarrying(player.getId())) return Anims.custom("carrying", Anims.CARRYING);   // someone over the shoulder (the knocked-out rework)
        if (com.dbzenith.combat.Wounds.wounded(player) > 0.25f && player.onGround() && !state.has(PublicStatePacket.FLYING)
                && !state.powering() && !state.has(PublicStatePacket.GUARDING)) return Anims.custom("wounded", Anims.WOUNDED);   // hunched, a hand on the wound (CX-52)
        if (state == null || player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isSwimming()
                || player.getPose() == Pose.SWIMMING) return null;
        if (state.has(PublicStatePacket.DOWNED)) return Anims.custom("downed", Anims.DOWNED);
        com.dbzenith.combat.engine.Fighter.State fs = FighterStates.get(player.getId());   // the engine's states (CX-19e)
        if (fs == com.dbzenith.combat.engine.Fighter.State.LAUNCHED) return Anims.custom("launched", Anims.LAUNCHED);
        if (fs == com.dbzenith.combat.engine.Fighter.State.STUNNED) return Anims.custom("stunned", Anims.STUNNED);
        if (state.has(PublicStatePacket.MEDITATING)) return Anims.MEDITATE;
        if (state.has(PublicStatePacket.GUARDING)) return Anims.GUARD;
        if (state.has(PublicStatePacket.HEAVY)) return Anims.HEAVY_WINDUP;
        var techCharge = com.dbzenith.client.ClientKiCharge.get(player.getId());             // charging a technique (CX-23)
        if (techCharge != null) return Anims.techHold(techCharge.kind(), techCharge.technique());   // its own pose if it has one (CX-49)
        if (state.has(PublicStatePacket.CHARGING) || state.has(PublicStatePacket.TRANSFORMING)) return Anims.CHARGE;
        if (com.dbzenith.client.motion.MotionEngine.enabled()) {                    // the motion engine moves the body now
            return null;                                                       // the fighting stance is the motion engine's, by PvP mode (CX-20)
        }
        if (state.has(PublicStatePacket.FLYING) && !player.onGround()) {
            double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double dy = player.getY() - player.yo;
            if (dy > 0.25 && horizontal < 0.4) return Anims.FLY_ASCEND;              // straight up
            if (dy < -0.35 && horizontal < 0.4) return Anims.FLY_DESCEND;            // dropping to land
            if (horizontal > 0.9) return Anims.FLY_FAST;                                 // no afterimages in flight (CX-32)
            if (horizontal > 0.12) return Anims.FLY_FORWARD;
            return Anims.FLY_HOVER;
        }
        double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (!player.onGround() || player.isCrouching()) return null;
        if (player.isSprinting() && horizontal > 0.24) return Anims.SPRINT;
        boolean bareHands = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
        if (!bareHands) return null;                                                   // items keep vanilla's arms
        if (state.has(PublicStatePacket.PVP) && horizontal < 0.15) return Anims.COMBAT_STANCE;   // PvP on: the fighting stance; off: a relaxed idle (CX-20)
        if (horizontal < 0.01) return Anims.idleFor(state.raceEnum());
        return null;
    }

    // ------------------------------------------------------------------ one-shot actions

    private static void tickActions(AbstractClientPlayer player, Track t, PublicStatePacket state, long now) {
        // transformations: the form changed while we were watching
        String form = state == null ? null : state.form();
        if (form != null && t.form != null && !form.equals(t.form)) {
            t.actionLockUntil = 0;                                             // a reversion must interrupt the growth/roar too
            if (PlayerData.BASE_FORM.equals(form)) play(player, t, Anims.POWER_DOWN, now, 0);
            else if (isApe(form)) {
                if (!isApe(t.form)) {
                    com.dbzenith.client.render.ApeTransformation.begin(player, t.previousState);
                    play(player, t, Anims.APE_ROAR, now, (int) Anims.APE_ROAR.endTick);
                    com.dbzenith.client.fx.GiantSteps.roar(player);
                }
            } else {
                play(player, t, Anims.transformFor(state.raceEnum()), now, 30);
                com.dbzenith.client.fx.TransformBurst.burst(player, form, state.auraColor());   // CX-24 phase 6
                if (player == Minecraft.getInstance().player) {
                    com.dbzenith.client.ui.CutInOverlay.play(net.minecraft.network.chat.Component.translatable(
                            com.dbzenith.transform.Forms.byId(form).translationKey()), state.auraColor());
                }
            }
        }
        t.form = form;
        t.previousState = state;

        // a heavy strike lands when the wind-up is released
        boolean heavy = state != null && state.has(PublicStatePacket.HEAVY);
        if (t.wasHeavy && !heavy && now - t.lastEventTick > 1) {      // a directional heavy brings its own move
            play(player, t, Anims.HEAVY_PUNCH, now, 0);
            t.suppressSwingUntil = now + 4;
        }
        t.wasHeavy = heavy;

        // bare-handed swings become a combo; a weapon or tool keeps vanilla's swing
        boolean swinging = player.swinging;
        if (!isApe(form) && swinging && !t.wasSwinging && now >= t.suppressSwingUntil && player.getMainHandItem().isEmpty()) {
            com.dbzenith.client.ClientSounds.swing(player);
            float pitch = player.getXRot();
            if (pitch < -35) play(player, t, Anims.LAUNCHER, now, 0);
            else if (!player.onGround() && pitch > 40) play(player, t, Anims.SPIKE, now, 0);
            else {
                if (now - t.lastPunch > 20) t.combo = 0;
                KeyframeAnimation[] chain = {Anims.JAB_RIGHT, Anims.CROSS_LEFT, Anims.JAB_RIGHT, Anims.HOOK, Anims.KICK};
                play(player, t, chain[t.combo % chain.length], now, 0);
                t.combo++;
            }
            t.lastPunch = now;
        }
        t.wasSwinging = swinging;

        // hit reactions
        if (player.hurtTime > t.lastHurtTime && player.isAlive()) {
            t.lastHurtTick = now;
            double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
            boolean big = dx * dx + dz * dz > 0.25;
            play(player, t, big ? Anims.custom("hit_heavy", Anims.HIT_HEAVY) : Anims.custom("hit_light", Anims.HIT_LIGHT), now, 0);
        }
        t.lastHurtTime = player.hurtTime;

        // back on your feet after being floored
        boolean downed = state != null && state.has(PublicStatePacket.DOWNED);
        if (t.wasDowned && !downed && now - t.lastEventTick > 2 && player.isAlive()) play(player, t, Anims.custom("get_up", Anims.GET_UP), now, 0);
        t.wasDowned = downed;
    }

    /** From the server: a technique was cast or a dash taken. */
    public static void onEvent(AnimEventPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (com.dbzenith.client.Prediction.swallow(msg)) return;            // already shown (phase 7)
        Entity e = mc.level.getEntity(msg.entityId());
        if (msg.kind() == AnimEventPacket.STUMBLE && e instanceof net.minecraft.world.entity.LivingEntity l && !(e instanceof AbstractClientPlayer)) {
            NpcActions.play(l, Anims.STUMBLE);                                  // an NPC fighter stumbling (CX-52)
            return;
        }
        if (!(e instanceof AbstractClientPlayer player)) return;
        Track t = TRACKS.computeIfAbsent(player, p -> new Track());
        long now = mc.level.getGameTime();
        if (msg.kind() == AnimEventPacket.STOP) {             // end the held pose (a beam struggle finished)
            ModifierLayer<IAnimation> layer = layer(player, ACTION_LAYER);
            if (layer != null) layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), null);
            t.actionLockUntil = 0;
            return;
        }
        if (msg.kind() == AnimEventPacket.FUSED) {                    // the fused warrior steps out of the light (12c)
            com.dbzenith.client.ClientFusion.fused(player, msg.data());
            t.lastEventTick = now;
            return;
        }
        KeyframeAnimation anim = switch (msg.kind()) {
            case AnimEventPacket.APE_STOMP -> msg.data() == 1 ? Anims.APE_STOMP_RIGHT : Anims.APE_STOMP_LEFT;
            case AnimEventPacket.VOLLEY -> Anims.KI_VOLLEY;
            case AnimEventPacket.BEAM -> Anims.beamFor(com.dbzenith.client.ClientKiCharge.lastTechnique(msg.entityId()), msg.data());
            case AnimEventPacket.THROW -> Anims.kiThrow(msg.data());
            case AnimEventPacket.WAVE -> Anims.KI_WAVE;
            case AnimEventPacket.FOCUS -> Anims.KI_FOCUS;
            case AnimEventPacket.DASH -> Anims.DASH;
            case AnimEventPacket.SWEEP -> Anims.SWEEP;
            case AnimEventPacket.UPPERCUT -> Anims.UPPERCUT;
            case AnimEventPacket.RUSH -> Anims.RUSH;
            case AnimEventPacket.HOOK -> Anims.HOOK;
            case AnimEventPacket.BREAKER -> Anims.BREAKER;
            case AnimEventPacket.DODGE -> msg.data() == 1 ? Anims.SIDE_LEFT : msg.data() == 2 ? Anims.SIDE_RIGHT : Anims.SPOT_DODGE;
            case AnimEventPacket.RECOVER -> msg.data() == 1 ? Anims.ROLL_UP : Anims.AIR_RECOVER;
            case AnimEventPacket.ZHIT -> Anims.ZHIT;
            case AnimEventPacket.VICTORY -> Anims.VICTORY;
            case AnimEventPacket.STUMBLE -> Anims.STUMBLE;
            default -> com.dbzenith.combat.Wounds.wounded(player) > 0.3f ? Anims.KI_BLAST_WOUNDED : Anims.KI_BLAST;   // one-handed, badly hurt (CX-52)
        };
        play(player, t, anim, now, msg.kind() == AnimEventPacket.APE_STOMP ? com.dbzenith.transform.ApeStomp.DURATION : 0);
        t.suppressSwingUntil = now + 3;
        t.lastEventTick = now;
        if (msg.kind() == AnimEventPacket.DASH) {
            var ps = com.dbzenith.client.ClientPublicStates.get(player.getId());
            boolean flying = ps != null && ps.has(PublicStatePacket.FLYING) && !player.onGround();
            if (!flying) com.dbzenith.client.fx.Afterimages.keepAlive(player, 8);   // none in flight (CX-32)
            if (player == mc.player) com.dbzenith.client.fx.CameraFx.rush();
        }
    }

    /** Play a one-shot on whichever player has this entity id (effects that know who did what). */
    public static void playOn(int entityId, KeyframeAnimation anim) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || entityId < 0) return;
        if (!(mc.level.getEntity(entityId) instanceof AbstractClientPlayer player)) {
            if (mc.level.getEntity(entityId) instanceof net.minecraft.world.entity.LivingEntity npc) NpcActions.play(npc, anim);
            return;
        }
        play(player, TRACKS.computeIfAbsent(player, p -> new Track()), anim, mc.level.getGameTime(), 0);
    }

    /** Play a one-shot that small actions (swings, hits) cannot cut short for {@code lockTicks} (a fusion dance). */
    public static void playLocked(int entityId, KeyframeAnimation anim, int lockTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || entityId < 0 || !(mc.level.getEntity(entityId) instanceof AbstractClientPlayer player)) return;
        Track t = TRACKS.computeIfAbsent(player, p -> new Track());
        t.actionLockUntil = 0;
        play(player, t, anim, mc.level.getGameTime(), lockTicks);
    }

    /** Clear a player's one-shot (a dance broken off). */
    public static void stop(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof AbstractClientPlayer player)) return;
        ModifierLayer<IAnimation> layer = layer(player, ACTION_LAYER);
        if (layer != null) layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), null);
        TRACKS.computeIfAbsent(player, p -> new Track()).actionLockUntil = 0;
    }

    /** Freeze a player's animations for {@code ticks} (the moment a blow connects). Ignored for anyone not a player. */
    public static void hitstop(int entityId, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || entityId < 0 || !com.dbzenith.config.DBZConfig.CLIENT.hitstop.get()) return;
        if (!(mc.level.getEntity(entityId) instanceof AbstractClientPlayer player)) {
            if (mc.level.getEntity(entityId) instanceof net.minecraft.world.entity.LivingEntity npc) NpcActions.hitstop(npc, ticks);   // NPCs (CX-19e)
            return;
        }
        Track t = TRACKS.computeIfAbsent(player, p -> new Track());
        t.hitstopUntil = Math.max(t.hitstopUntil, mc.level.getGameTime() + ticks);
        tickHitstop(player, t, mc.level.getGameTime());
    }

    /** One arm left (the left): every stance and action mirrored, so the left does what the right would (the limbs rework). */
    private static void tickMirror(AbstractClientPlayer player) {
        var mirrors = MIRRORS.get(player);
        if (mirrors == null) return;
        boolean left = com.dbzenith.combat.Limbs.of(player).leftHanded();
        for (var m : mirrors) if (m != null && m.isEnabled() != left) m.setEnabled(left);
    }

    private static void tickHitstop(AbstractClientPlayer player, Track t, long now) {
        SpeedModifier[] speeds = SPEEDS.get(player);
        if (speeds == null) return;
        float s = now < t.hitstopUntil ? 0f : 1f;
        for (SpeedModifier m : speeds) if (m != null) m.speed = s;
    }

    private static void play(AbstractClientPlayer player, Track t, KeyframeAnimation anim, long now, int lockTicks) {
        if (now < t.actionLockUntil && lockTicks == 0) return;
        anim = Anims.custom(anim);                                             // an artist's file in its place (CX-19 phase 10)
        ModifierLayer<IAnimation> layer = layer(player, ACTION_LAYER);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD), new KeyframeAnimationPlayer(anim));
        t.actionLockUntil = now + lockTicks;
    }

    /** Dev automation: play an animation from {@link Anims} by field name (BEAM / THROW for the timed ones) on the local player. */
    /** Dev automation: animations held still (a frame picked with _f<N>). */
    public static boolean devFreeze;
    private static KeyframeAnimation devAnim;
    private static int devFrame;

    public static void devPreview(String name) {
        devPreview(name, false);
    }

    /**
     * Dev automation: {@code duet} also stands a stand-in partner beside the local player (on the right for the fusion
     * dance, facing them for the Potara) playing the other half (an {@code _A} animation's {@code _B}) on the same frame.
     */
    public static void devPreview(String name, boolean duet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int frame = -1;
        java.util.regex.Matcher fm = java.util.regex.Pattern.compile("(.*)_f([0-9]+)$").matcher(name);
        if (fm.matches()) {
            name = fm.group(1);
            frame = Integer.parseInt(fm.group(2));
        }
        KeyframeAnimation anim = devAnimation(name);
        if (anim == null) return;
        ModifierLayer<IAnimation> layer = layer(mc.player, ACTION_LAYER);
        if (layer != null) layer.setAnimation(frozen(anim, frame));
        devFreeze = frame >= 0;
        devAnim = anim;
        devFrame = frame;
        com.dbzenith.client.DevDuet.clear();
        if (duet) {
            KeyframeAnimation other = name.endsWith("_A") ? devAnimation(name.substring(0, name.length() - 2) + "_B") : anim;
            AbstractClientPlayer partner = com.dbzenith.client.DevDuet.spawn(name.startsWith("POTARA"));
            ModifierLayer<IAnimation> pl = partner == null || other == null ? null : layer(partner, ACTION_LAYER);
            if (pl != null) pl.setAnimation(frozen(other, frame));
        }
    }

    private static final Map<String, KeyframeAnimation> CLIPS = new java.util.HashMap<>();

    /** A combat-engine move started (CX-19): play its clip, named as in {@link Anims} (JAB_RIGHT, LAUNCHER, ...). */
    /** A move the server started (phase 7): for yourself, only if your client didn't already show it. */
    public static void onMove(int entityId, String clip, String moveId) {
        Minecraft mc = Minecraft.getInstance();
        com.dbzenith.client.fx.HitboxOverlay.started(entityId, moveId);       // the frame data (phase 9)
        if (mc.player != null && entityId == mc.player.getId() && !com.dbzenith.client.Prediction.confirm(moveId)) return;
        playClip(entityId, clip);
    }

    public static void playClip(int entityId, String clip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        KeyframeAnimation anim = Anims.custom(clip, CLIPS.computeIfAbsent(clip.toUpperCase(java.util.Locale.ROOT), AnimController::devAnimation));   // an artist's file wins (phase 10)
        if (anim == null) return;
        if (!(mc.level.getEntity(entityId) instanceof AbstractClientPlayer player)) {          // an NPC's move (CX-19e)
            if (mc.level.getEntity(entityId) instanceof net.minecraft.world.entity.LivingEntity npc) NpcActions.play(npc, anim);
            return;
        }
        Track t = TRACKS.computeIfAbsent(player, p -> new Track());
        long now = mc.level.getGameTime();
        t.actionLockUntil = 0;                                       // a new blow always takes over from the last
        play(player, t, anim, now, 0);
        t.lastEventTick = now;
        t.lastPunch = now;
        com.dbzenith.client.ClientSounds.swing(player);
    }

    private static KeyframeAnimation devAnimation(String name) {
        if (name.equals("BEAM")) return Anims.kiBeam(40);
        if (name.equals("THROW")) return Anims.kiThrow(30);
        try {
            return (KeyframeAnimation) Anims.class.getField(name).get(null);
        } catch (ReflectiveOperationException e) {
            DBZenith.LOGGER.warn("[dev] no animation {}", name);
            return null;
        }
    }

    private static KeyframeAnimationPlayer frozen(KeyframeAnimation anim, int stopAt) {
        return stopAt < 0 ? new KeyframeAnimationPlayer(anim) : new KeyframeAnimationPlayer(anim) {
            int ticks;

            @Override
            public void tick() {                                                    // runs up to the chosen frame, then holds it
                if (ticks++ < stopAt - 1) super.tick();
            }
        };
    }

    /** The Great Ape forms (the player's own figure grown giant: it roars on the change instead of the usual burst). */
    private static boolean isApe(String form) {
        return com.dbzenith.transform.GreatApe.isApeForm(form);
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player, ResourceLocation id) {
        Object o = PlayerAnimationAccess.getPlayerAssociatedData(player).get(id);
        return o instanceof ModifierLayer<?> m ? (ModifierLayer<IAnimation>) m : null;
    }
}
