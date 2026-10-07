package com.dbzenith.duel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One duel (CX-19 phase 9): two players, a virtual arena (a circle round the spot where it began), best of 1, 3 or 5
 * rounds, under full or melee-only rules. {@link Duels} runs it; this holds its state and the fight's numbers.
 */
public final class Duel {
    public enum Rules { FULL, MELEE }

    public enum Phase { COUNTDOWN, FIGHTING, BETWEEN, OVER }

    /** What each side did, for the results screen. */
    public static final class Stats {
        public double damage;
        public int hits, maxCombo, perfectGuards, vanishes;

        public double[] toArray() {
            return new double[]{damage, hits, maxCombo, perfectGuards, vanishes};
        }
    }

    /** Someone watching, and where (and how) to put them back. */
    record Watcher(UUID id, net.minecraft.world.level.GameType mode, Vec3 pos, float yaw, float pitch) {}

    final UUID[] ids = new UUID[2];
    final String[] names = new String[2];
    final int bestOf;
    final Rules rules;
    final ServerLevel level;
    final Vec3 center;
    final Vec3[] starts = new Vec3[2];
    final int[] wins = new int[2];
    final Stats[] stats = {new Stats(), new Stats()};
    final long[] outSince = {-1, -1};
    final Map<UUID, Watcher> watchers = new HashMap<>();
    Phase phase = Phase.COUNTDOWN;
    long phaseStart, roundStart;
    int round = 1;

    Duel(ServerPlayer a, ServerPlayer b, int bestOf, Rules rules) {
        ids[0] = a.getUUID();
        ids[1] = b.getUUID();
        names[0] = a.getGameProfile().getName();
        names[1] = b.getGameProfile().getName();
        this.bestOf = bestOf;
        this.rules = rules;
        this.level = a.serverLevel();
        this.center = a.position().add(b.position()).scale(0.5);
    }

    public int indexOf(UUID id) {
        return ids[0].equals(id) ? 0 : ids[1].equals(id) ? 1 : -1;
    }

    public boolean has(UUID id) {
        return indexOf(id) >= 0;
    }

    public ServerPlayer player(int i) {
        return level.getServer().getPlayerList().getPlayer(ids[i]);
    }

    public Phase phase() {
        return phase;
    }

    public Rules rules() {
        return rules;
    }

    public Stats stats(int i) {
        return stats[i];
    }

    public int wins(int i) {
        return wins[i];
    }

    public Vec3 center() {
        return center;
    }
}
