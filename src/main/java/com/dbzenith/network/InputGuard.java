package com.dbzenith.network;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Anti-cheat basics for player inputs (CX-19 phase 7). Every combat input packet passes {@link #allow} first:
 * <ul>
 *   <li><b>state</b>: no inputs from the dead, spectators or the sleeping;</li>
 *   <li><b>rate</b>: a token bucket per input kind (a burst, refilled at a steady rate per second, both well above what
 *       hands can do), so a macro or a flooding client gets its extra presses dropped;</li>
 *   <li><b>violations</b> are counted; past {@code inputViolationKick} in a minute the player is kicked (0 = never), and
 *       the log hears about it once every ten seconds at most.</li>
 * </ul>
 * Everything an input then asks for (costs, cooldowns, reach, what move) is still decided by the server.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InputGuard {
    private static final Logger LOG = LogUtils.getLogger();

    /** Kinds of input, each with its burst and its refill per second. */
    public enum Kind {
        MELEE(8, 14), KI_BLAST(12, 24), DASH(4, 6), TECHNIQUE(4, 8), BEAM_MASH(10, 25), LOCK_ON(4, 8), TOGGLE(4, 6), INPUT(16, 30);

        final int burst;
        final double perSecond;

        Kind(int burst, double perSecond) {
            this.burst = burst;
            this.perSecond = perSecond;
        }
    }

    private static final class State {
        final double[] tokens = new double[Kind.values().length];
        final long[] last = new long[Kind.values().length];
        int violations;
        long windowStart, lastLog;
        long dropped, accepted;

        State() {
            for (Kind k : Kind.values()) tokens[k.ordinal()] = k.burst;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private InputGuard() {}

    /** May this player's input of this kind go through? Drops it (and counts it) if not. */
    public static boolean allow(ServerPlayer p, Kind kind) {
        if (p == null || !p.isAlive() || p.isSpectator() || p.isSleeping() || p.isRemoved()) return false;
        State s = STATES.computeIfAbsent(p.getUUID(), u -> new State());
        long now = System.nanoTime();
        int i = kind.ordinal();
        double scale = scale();
        if (scale <= 0) return true;                                            // limits off
        if (s.last[i] != 0) {
            s.tokens[i] = Math.min(kind.burst * scale, s.tokens[i] + (now - s.last[i]) / 1e9 * kind.perSecond * scale);
        }
        s.last[i] = now;
        if (s.tokens[i] >= 1) {
            s.tokens[i] -= 1;
            s.accepted++;
            return true;
        }
        s.dropped++;
        violation(p, s, kind, now);
        return false;
    }

    /** A value outside what the client could ever send (NaN, out of range): reject it, and count it. */
    public static boolean sane(ServerPlayer p, boolean ok, String what) {
        if (ok || p == null) return ok;
        State s = STATES.computeIfAbsent(p.getUUID(), u -> new State());
        s.dropped++;
        violation(p, s, null, System.nanoTime());
        if (System.nanoTime() - s.lastLog > 10_000_000_000L) LOG.warn("Rejected a malformed input from {}: {}", p.getGameProfile().getName(), what);
        return false;
    }

    private static void violation(ServerPlayer p, State s, Kind kind, long now) {
        if (now - s.windowStart > 60_000_000_000L) {
            s.windowStart = now;
            s.violations = 0;
        }
        s.violations++;
        if (now - s.lastLog > 10_000_000_000L) {
            s.lastLog = now;
            LOG.warn("Input from {} dropped ({}): {} violation(s) this minute", p.getGameProfile().getName(), kind == null ? "malformed" : kind, s.violations);
        }
        int kick = kickAt();
        if (kick > 0 && s.violations >= kick) {
            s.violations = 0;
            p.connection.disconnect(Component.translatable("message.dbzenith.input_flood"));
        }
    }

    private static double scale() {
        try {
            return DBZConfig.SERVER.inputRateScale.get();
        } catch (IllegalStateException e) {
            return 1;
        }
    }

    private static int kickAt() {
        try {
            return DBZConfig.SERVER.inputViolationKick.get();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    /** For /dbz netstats: accepted and dropped inputs, violations this minute. */
    public static long[] stats(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? new long[]{0, 0, 0} : new long[]{s.accepted, s.dropped, s.violations};
    }

    /** Tests: forget a player's buckets. */
    public static void reset(ServerPlayer p) {
        STATES.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
