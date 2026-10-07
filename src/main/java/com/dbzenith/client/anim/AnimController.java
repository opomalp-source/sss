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

    private AnimController() {}

    /** Called once from client setup. */
    public static void registerLayers() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(MOTION_LAYER, 900,
                player -> com.dbzenith.client.motion.MotionAnimation.of(com.dbzenith.client.motion.MotionEngine.get(player)));
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(STATE_LAYER, 1000, player -> withSpeed(player, 0));
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ACTION_LAYER, 1500, player -> withSpeed(player, 1));
    }

    /** Each layer carries a speed control, so a landed blow can freeze both fighters for a few frames (hitstop). */
    private static ModifierLayer<IAnimation> withSpeed(AbstractClientPlayer player, int slot) {
        SpeedModifier speed = new SpeedModifier(1f);
        SPEEDS.computeIfAbsent(player, p -> new SpeedModifier[2])[slot] = speed;
        return new ModifierLayer<>(null, speed);
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
            tickHitstop(player, t, now);
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
                want == null ? null : new KeyframeAnimationPlayer(want));
        t.loop = want;
        t.candidate = null;
    }

    private static KeyframeAnimation chooseStance(AbstractClientPlayer player, PublicStatePacket state, Track t, long now) {
        if (state == null || player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isSwimming()
                || player.getPose() == Pose.SWIMMING || isApe(state.form())) return null;
        if (state.has(PublicStatePacket.DOWNED)) return Anims.DOWNED;
        if (state.has(PublicStatePacket.MEDITATING)) return Anims.MEDITATE;
        if (state.has(PublicStatePacket.GUARDING)) return Anims.GUARD;
        if (state.has(PublicStatePacket.HEAVY)) return Anims.HEAVY_WINDUP;
        if (state.has(PublicStatePacket.CHARGING) || state.has(PublicStatePacket.TRANSFORMING)) return Anims.CHARGE;
        if (com.dbzenith.client.motion.MotionEngine.enabled()) {                    // the motion engine moves the body now
            double mx = player.getX() - player.xo, mz = player.getZ() - player.zo;
            boolean bare = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
            boolean fresh = now - t.lastPunch < 100 || now - t.lastHurtTick < 100;
            return bare && fresh && player.onGround() && mx * mx + mz * mz < 0.0225 ? Anims.COMBAT_STANCE : null;
        }
        if (state.has(PublicStatePacket.FLYING) && !player.onGround()) {
            double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double dy = player.getY() - player.yo;
            if (dy > 0.25 && horizontal < 0.4) return Anims.FLY_ASCEND;              // straight up
            if (dy < -0.35 && horizontal < 0.4) return Anims.FLY_DESCEND;            // dropping to land
            if (horizontal > 0.9) {
                com.dbzenith.client.fx.Afterimages.keepAlive(player, 3);
                return Anims.FLY_FAST;
            }
            if (horizontal > 0.12) return Anims.FLY_FORWARD;
            return Anims.FLY_HOVER;
        }
        double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (!player.onGround() || player.isCrouching()) return null;
        if (player.isSprinting() && horizontal > 0.24) return Anims.SPRINT;
        boolean bareHands = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
        if (!bareHands) return null;                                                   // items keep vanilla's arms
        if ((now - t.lastPunch < 100 || now - t.lastHurtTick < 100) && horizontal < 0.15) return Anims.COMBAT_STANCE;
        if (horizontal < 0.01) return Anims.idleFor(state.raceEnum());
        return null;
    }

    // ------------------------------------------------------------------ one-shot actions

    private static void tickActions(AbstractClientPlayer player, Track t, PublicStatePacket state, long now) {
        // transformations: the form changed while we were watching
        String form = state == null ? null : state.form();
        if (form != null && t.form != null && !form.equals(t.form) && !isApe(form)) {
            if (PlayerData.BASE_FORM.equals(form)) play(player, t, Anims.POWER_DOWN, now, 0);
            else {
                play(player, t, Anims.transformFor(state.raceEnum()), now, 30);
                com.dbzenith.client.fx.ImpactFx.transformBurst(player, state.auraColor());
                if (player == Minecraft.getInstance().player) {
                    com.dbzenith.client.ui.CutInOverlay.play(net.minecraft.network.chat.Component.translatable(
                            com.dbzenith.transform.Forms.byId(form).translationKey()), state.auraColor());
                }
            }
        }
        t.form = form;

        // a heavy strike lands when the wind-up is released
        boolean heavy = state != null && state.has(PublicStatePacket.HEAVY);
        if (t.wasHeavy && !heavy && now - t.lastEventTick > 1) {      // a directional heavy brings its own move
            play(player, t, Anims.HEAVY_PUNCH, now, 0);
            t.suppressSwingUntil = now + 4;
        }
        t.wasHeavy = heavy;

        // bare-handed swings become a combo; a weapon or tool keeps vanilla's swing
        boolean swinging = player.swinging;
        if (swinging && !t.wasSwinging && now >= t.suppressSwingUntil && player.getMainHandItem().isEmpty()) {
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
            play(player, t, big ? Anims.HIT_HEAVY : Anims.HIT_LIGHT, now, 0);
        }
        t.lastHurtTime = player.hurtTime;

        // back on your feet after being floored
        boolean downed = state != null && state.has(PublicStatePacket.DOWNED);
        if (t.wasDowned && !downed && now - t.lastEventTick > 2 && player.isAlive()) play(player, t, Anims.GET_UP, now, 0);
        t.wasDowned = downed;
    }

    /** From the server: a technique was cast or a dash taken. */
    public static void onEvent(AnimEventPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entityId());
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
            case AnimEventPacket.VOLLEY -> Anims.KI_VOLLEY;
            case AnimEventPacket.BEAM -> Anims.kiBeam(msg.data());
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
            default -> Anims.KI_BLAST;
        };
        play(player, t, anim, now, 0);
        t.suppressSwingUntil = now + 3;
        t.lastEventTick = now;
        if (msg.kind() == AnimEventPacket.DASH) {
            com.dbzenith.client.fx.Afterimages.keepAlive(player, 8);
            if (player == mc.player) com.dbzenith.client.fx.CameraFx.rush();
        }
    }

    /** Play a one-shot on whichever player has this entity id (effects that know who did what). */
    public static void playOn(int entityId, KeyframeAnimation anim) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || entityId < 0 || !(mc.level.getEntity(entityId) instanceof AbstractClientPlayer player)) return;
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
        if (!(mc.level.getEntity(entityId) instanceof AbstractClientPlayer player)) return;
        Track t = TRACKS.computeIfAbsent(player, p -> new Track());
        t.hitstopUntil = Math.max(t.hitstopUntil, mc.level.getGameTime() + ticks);
        tickHitstop(player, t, mc.level.getGameTime());
    }

    private static void tickHitstop(AbstractClientPlayer player, Track t, long now) {
        SpeedModifier[] speeds = SPEEDS.get(player);
        if (speeds == null) return;
        float s = now < t.hitstopUntil ? 0f : 1f;
        for (SpeedModifier m : speeds) if (m != null) m.speed = s;
    }

    private static void play(AbstractClientPlayer player, Track t, KeyframeAnimation anim, long now, int lockTicks) {
        if (now < t.actionLockUntil && lockTicks == 0) return;
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
    public static void playClip(int entityId, String clip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof AbstractClientPlayer player)) return;
        KeyframeAnimation anim = CLIPS.computeIfAbsent(clip.toUpperCase(java.util.Locale.ROOT), AnimController::devAnimation);
        if (anim == null) return;
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

    /** The Great Ape replaces the whole player model, so the humanoid animations do not apply. */
    private static boolean isApe(String form) {
        return com.dbzenith.transform.GreatApe.isApeForm(form);
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player, ResourceLocation id) {
        Object o = PlayerAnimationAccess.getPlayerAssociatedData(player).get(id);
        return o instanceof ModifierLayer<?> m ? (ModifierLayer<IAnimation>) m : null;
    }
}
