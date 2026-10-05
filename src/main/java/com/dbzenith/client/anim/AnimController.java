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

    private static final Map<AbstractClientPlayer, Track> TRACKS = new WeakHashMap<>();
    /** A stance must hold this many ticks before the loop swaps, so brief flickers in speed do not jitter the pose. */
    private static final int STATE_SETTLE = 3;
    private static final Map<AbstractClientPlayer, SpeedModifier[]> SPEEDS = new WeakHashMap<>();

    private AnimController() {}

    /** Called once from client setup. */
    public static void registerLayers() {
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
            tickStance(player, t, state);
            tickActions(player, t, state, now);
        }
    }

    // ------------------------------------------------------------------ stance loops

    private static void tickStance(AbstractClientPlayer player, Track t, PublicStatePacket state) {
        KeyframeAnimation want = chooseStance(player, state);
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

    private static KeyframeAnimation chooseStance(AbstractClientPlayer player, PublicStatePacket state) {
        if (state == null || player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isSwimming()
                || player.getPose() == Pose.SWIMMING || isApe(state.form())) return null;
        if (state.has(PublicStatePacket.MEDITATING)) return Anims.MEDITATE;
        if (state.has(PublicStatePacket.GUARDING)) return Anims.GUARD;
        if (state.has(PublicStatePacket.HEAVY)) return Anims.HEAVY_WINDUP;
        if (state.has(PublicStatePacket.CHARGING)) return Anims.CHARGE;
        if (state.has(PublicStatePacket.FLYING) && !player.onGround()) {
            double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > 0.9) {
                com.dbzenith.client.fx.Afterimages.keepAlive(player, 3);
                return Anims.FLY_FAST;
            }
            if (horizontal > 0.12) return Anims.FLY_FORWARD;
            return Anims.FLY_HOVER;
        }
        return null;
    }

    // ------------------------------------------------------------------ one-shot actions

    private static void tickActions(AbstractClientPlayer player, Track t, PublicStatePacket state, long now) {
        // transformations: the form changed while we were watching
        String form = state == null ? null : state.form();
        if (form != null && t.form != null && !form.equals(t.form) && !isApe(form)) {
            if (PlayerData.BASE_FORM.equals(form)) play(player, t, Anims.POWER_DOWN, now, 0);
            else {
                play(player, t, Anims.TRANSFORM, now, 30);
                com.dbzenith.client.fx.ImpactFx.transformBurst(player, state.auraColor());
            }
        }
        t.form = form;

        // a heavy strike lands when the wind-up is released
        boolean heavy = state != null && state.has(PublicStatePacket.HEAVY);
        if (t.wasHeavy && !heavy) {
            play(player, t, Anims.HEAVY_PUNCH, now, 0);
            t.suppressSwingUntil = now + 4;
        }
        t.wasHeavy = heavy;

        // bare-handed swings become a combo; a weapon or tool keeps vanilla's swing
        boolean swinging = player.swinging;
        if (swinging && !t.wasSwinging && now >= t.suppressSwingUntil && player.getMainHandItem().isEmpty()) {
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
            double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
            boolean big = dx * dx + dz * dz > 0.25;
            play(player, t, big ? Anims.HIT_HEAVY : Anims.HIT_LIGHT, now, 0);
        }
        t.lastHurtTime = player.hurtTime;
    }

    /** From the server: a technique was cast or a dash taken. */
    public static void onEvent(AnimEventPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entityId());
        if (!(e instanceof AbstractClientPlayer player)) return;
        Track t = TRACKS.computeIfAbsent(player, p -> new Track());
        long now = mc.level.getGameTime();
        KeyframeAnimation anim = switch (msg.kind()) {
            case AnimEventPacket.VOLLEY -> Anims.KI_VOLLEY;
            case AnimEventPacket.BEAM -> Anims.kiBeam(msg.data());
            case AnimEventPacket.THROW -> Anims.kiThrow(msg.data());
            case AnimEventPacket.WAVE -> Anims.KI_WAVE;
            case AnimEventPacket.FOCUS -> Anims.KI_FOCUS;
            case AnimEventPacket.DASH -> Anims.DASH;
            default -> Anims.KI_BLAST;
        };
        play(player, t, anim, now, 0);
        t.suppressSwingUntil = now + 3;
        if (msg.kind() == AnimEventPacket.DASH) {
            com.dbzenith.client.fx.Afterimages.keepAlive(player, 8);
            if (player == mc.player) com.dbzenith.client.fx.CameraFx.rush();
        }
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
    public static void devPreview(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        KeyframeAnimation anim;
        if (name.equals("BEAM")) anim = Anims.kiBeam(40);
        else if (name.equals("THROW")) anim = Anims.kiThrow(30);
        else {
            try {
                anim = (KeyframeAnimation) Anims.class.getField(name).get(null);
            } catch (ReflectiveOperationException e) {
                DBZenith.LOGGER.warn("[dev] no animation {}", name);
                return;
            }
        }
        ModifierLayer<IAnimation> layer = layer(mc.player, ACTION_LAYER);
        if (layer != null) layer.setAnimation(new KeyframeAnimationPlayer(anim));
    }

    /** The Great Ape replaces the whole player model, so the humanoid animations do not apply. */
    private static boolean isApe(String form) {
        return com.dbzenith.transform.Forms.GREAT_APE.id().equals(form);
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player, ResourceLocation id) {
        Object o = PlayerAnimationAccess.getPlayerAssociatedData(player).get(id);
        return o instanceof ModifierLayer<?> m ? (ModifierLayer<IAnimation>) m : null;
    }
}
