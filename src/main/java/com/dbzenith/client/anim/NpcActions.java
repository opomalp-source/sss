package com.dbzenith.client.anim;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.engine.Fighter;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;

/**
 * Combat animations for NPCs (CX-19e), the same clips players get. Each NPC drawn with the motion engine's model gets a
 * playerAnimator stack: the motion engine underneath, then a stance layer (stunned, launched, knocked down, guarding,
 * from the server's {@code FighterStatePacket}s), then an action layer (its moves, from {@code MoveAnimPacket}s, and its
 * hit reactions). Both upper layers carry a speed control for hitstop.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class NpcActions {
    private static final int MOTION = 1000, STANCE = 1200, ACTION = 1500;

    private static final class Track {
        final LivingEntity entity;
        final AnimationStack stack = new AnimationStack();
        final SpeedModifier stanceSpeed = new SpeedModifier(1f), actionSpeed = new SpeedModifier(1f);
        final ModifierLayer<IAnimation> stance = new ModifierLayer<>(null, stanceSpeed), action = new ModifierLayer<>(null, actionSpeed);
        IAnimation motion;
        Fighter.State shown = Fighter.State.IDLE;
        long hitstopUntil, heavyHintUntil, actionAt = Long.MIN_VALUE / 2;
        int lastHurtTime;

        Track(LivingEntity entity) {
            this.entity = entity;
            stack.addAnimLayer(STANCE, stance);
            stack.addAnimLayer(ACTION, action);
        }
    }

    private static final Int2ObjectOpenHashMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

    private NpcActions() {}

    private static Track track(LivingEntity e) {
        Track t = TRACKS.get(e.getId());
        if (t == null || t.entity != e) {
            t = new Track(e);
            TRACKS.put(e.getId(), t);
        }
        return t;
    }

    /** The animation to run on an NPC's model: its motion with the combat layers over it. */
    public static IAnimation stack(LivingEntity e, IAnimation motion) {
        Track t = track(e);
        if (t.motion != motion) {
            if (t.motion != null) t.stack.removeLayer(t.motion);
            t.motion = motion;
            t.stack.addAnimLayer(MOTION, motion);
        }
        return t.stack;
    }

    /** The whole-figure source for an NPC (its stack if it has one, else just the motion). */
    public static IAnimation bodySource(LivingEntity e, IAnimation motion) {
        Track t = TRACKS.get(e.getId());
        return t != null && t.entity == e && t.motion == motion ? t.stack : motion;
    }

    /** A one-shot on the action layer: a move, a reaction. */
    public static void play(LivingEntity e, KeyframeAnimation anim) {
        Track t = track(e);
        t.action.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD), new KeyframeAnimationPlayer(anim));
        if (Minecraft.getInstance().level != null) t.actionAt = Minecraft.getInstance().level.getGameTime();
    }

    public static void hitstop(LivingEntity e, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Track t = track(e);
        t.hitstopUntil = Math.max(t.hitstopUntil, mc.level.getGameTime() + ticks);
        t.stanceSpeed.speed = 0f;
        t.actionSpeed.speed = 0f;
    }

    /** The next hurt of this NPC came from a heavy blow (from the impact): it reels harder. */
    public static void heavyHint(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Track t = TRACKS.get(entityId);
        if (t != null && mc.level != null) t.heavyHintUntil = mc.level.getGameTime() + 3;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRACKS.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        Iterator<Track> it = TRACKS.values().iterator();
        while (it.hasNext()) {
            Track t = it.next();
            LivingEntity e = t.entity;
            if (e.isRemoved() || e.level() != mc.level) {
                it.remove();
                continue;
            }
            if (now >= t.hitstopUntil && t.actionSpeed.speed == 0f) {
                t.stanceSpeed.speed = 1f;
                t.actionSpeed.speed = 1f;
            }
            stance(t, FighterStates.get(e.getId()));
            if (e.hurtTime > t.lastHurtTime && e.isAlive() && now - t.actionAt > 1) {     // struck: reel (unless its own move just began)
                boolean heavy = now <= t.heavyHintUntil || t.shown == Fighter.State.LAUNCHED || t.shown == Fighter.State.KNOCKDOWN;
                play(e, heavy ? Anims.HIT_HEAVY : Anims.HIT_LIGHT);
            }
            t.lastHurtTime = e.hurtTime;
            t.stack.tick();
        }
    }

    private static void stance(Track t, Fighter.State s) {
        if (s == t.shown) return;
        Fighter.State was = t.shown;
        t.shown = s;
        KeyframeAnimation loop = switch (s) {
            case KNOCKDOWN -> Anims.DOWNED;
            case STUNNED -> Anims.STUNNED;
            case LAUNCHED -> Anims.LAUNCHED;
            case GUARDING -> Anims.GUARD;
            default -> null;
        };
        t.stance.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), loop == null ? null : new KeyframeAnimationPlayer(loop));
        if (was == Fighter.State.KNOCKDOWN && s != Fighter.State.KNOCKDOWN && t.entity.isAlive()) play(t.entity, Anims.GET_UP);
    }
}
