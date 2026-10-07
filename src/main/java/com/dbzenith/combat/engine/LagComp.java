package com.dbzenith.combat.engine;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Lag compensation (CX-19 phase 7). A player sees everyone else where they were a little while ago: half the round
 * trip for the server's word to arrive, plus a tick of the client's own smoothing. So the server keeps where each living
 * thing near a player was over the last {@link #SIZE} ticks, and a player's blow is tested against the victim both where
 * they are now and where the attacker saw them ({@link #rewindTicks}, at most {@code lagCompensationTicks}).
 * <p>
 * The history is a fixed ring of boxes per entity, written in place each tick: no allocation once it exists.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LagComp {
    static final int SIZE = 24;
    private static final double NEAR_PLAYER = 48;

    private static final class History {
        final double[] box = new double[SIZE * 6];
        final long[] tick = new long[SIZE];

        History() {
            java.util.Arrays.fill(tick, Long.MIN_VALUE);
        }

        void record(AABB b, long now) {
            int i = (int) Math.floorMod(now, (long) SIZE), o = i * 6;
            box[o] = b.minX;
            box[o + 1] = b.minY;
            box[o + 2] = b.minZ;
            box[o + 3] = b.maxX;
            box[o + 4] = b.maxY;
            box[o + 5] = b.maxZ;
            tick[i] = now;
        }

        AABB at(long when) {
            int i = (int) Math.floorMod(when, (long) SIZE), o = i * 6;
            return tick[i] == when ? new AABB(box[o], box[o + 1], box[o + 2], box[o + 3], box[o + 4], box[o + 5]) : null;
        }
    }

    private static final Map<LivingEntity, History> HISTORY = new IdentityHashMap<>();

    private LagComp() {}

    /** Every living thing near a player, every server tick: where it is. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || maxTicks() <= 0) return;
        if (!(e instanceof ServerPlayer) && !e.level().hasNearbyAlivePlayer(e.getX(), e.getY(), e.getZ(), NEAR_PLAYER)) return;
        HISTORY.computeIfAbsent(e, k -> new History()).record(e.getBoundingBox(), e.level().getGameTime());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity l) HISTORY.remove(l);
    }

    static int maxTicks() {
        try {
            return DBZConfig.SERVER.lagCompensationTicks.get();
        } catch (IllegalStateException e) {
            return 6;
        }
    }

    /** How far back an attacker saw the world: half their ping in ticks, plus one for interpolation, capped. */
    public static int rewindTicks(LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayer p)) return 0;
        int max = maxTicks();
        if (max <= 0) return 0;
        return Math.min(max, (int) Math.round(p.latency / 2.0 / 50.0) + 1);
    }

    /** Where {@code e} was {@code ticksAgo} ticks ago, or null if not known. */
    public static AABB boxAt(LivingEntity e, int ticksAgo) {
        History h = HISTORY.get(e);
        return h == null ? null : h.at(e.level().getGameTime() - ticksAgo);
    }

    /** Tests and /dbz netstats: how many entities have a history. */
    public static int tracked() {
        return HISTORY.size();
    }
}
