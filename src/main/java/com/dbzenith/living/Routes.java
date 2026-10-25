package com.dbzenith.living;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Long walks across a town (docs/WORLD_STRUCTURES.md). A resident's navigation searches a box round where it stands
 * (its follow range) with a limited number of steps, so heading straight for a place far off it ends up against the
 * far wall of a building it walked into, nearest its goal but shut in. A town may therefore keep a map: waypoints
 * outdoors (along its streets, the step before every door) and its buildings as rooms, each with the waypoint at its
 * door. A walk longer than {@link #FAR}, or out of one building into another, goes out by the door, from waypoint to
 * waypoint (legs of at most {@link #LEG}, only where the ground between is open, as the world shows it), and in by
 * the other door. Towns without a map are walked straight, as before.
 */
public final class Routes {
    /** The longest leg between waypoints: inside a resident's search box (follow range 40). */
    public static final double LEG = 36;
    /** Farther than this (or in another building), a walk goes by the map. */
    public static final double FAR = 30;
    /** A waypoint this close is reached already (the next one is taken). */
    static final double HERE = 2.5;

    private Routes() {}

    /** A building: the box it fills (or the round tower or dome in it), and the waypoint at its door (its only way in and out). */
    public record Room(int x0, int y0, int z0, int x1, int y1, int z1, int door, boolean round) {
        public boolean contains(BlockPos p) {
            if (p.getX() < x0 || p.getX() > x1 || p.getY() < y0 || p.getY() > y1 || p.getZ() < z0 || p.getZ() > z1) return false;
            if (!round) return true;
            double r = (x1 - x0) / 2.0, dx = p.getX() - (x0 + r), dz = p.getZ() - (z0 + r);
            return dx * dx + dz * dz <= r * r + r;
        }

        public int[] save() {
            return new int[]{x0, y0, z0, x1, y1, z1, door, round ? 1 : 0};
        }

        public static Room load(int[] a) {
            return a.length >= 7 ? new Room(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a.length > 7 && a[7] == 1) : null;
        }
    }

    /** A town's map: its waypoints and rooms, and which waypoints are joined by open ground (learned from the world, kept). */
    public static final class Map {
        public final List<BlockPos> ways;
        public final List<Room> rooms;
        /** Per pair of waypoints: 0 not looked at yet, 1 open ground between, 2 not. */
        private final byte[] joined;
        /** The room each waypoint is in (-1 outdoors), worked out once. */
        private int[] wayRoom;

        public Map(List<BlockPos> ways, List<Room> rooms) {
            this.ways = List.copyOf(ways);
            this.rooms = List.copyOf(rooms);
            this.joined = new byte[this.ways.size() * this.ways.size()];
        }

        public boolean isEmpty() {
            return ways.isEmpty();
        }

        /** The room a spot is in (its index), or -1 outdoors. */
        int roomOf(BlockPos p) {
            for (int i = 0; i < rooms.size(); i++) if (rooms.get(i).contains(p)) return i;
            return -1;
        }

        /** Whether two waypoints are joined: in the same building always (a resident finds its way about one), outdoors over open ground. */
        boolean joined(LevelReader level, int i, int j) {
            if (wayRoom == null) {
                wayRoom = new int[ways.size()];
                for (int w = 0; w < ways.size(); w++) wayRoom[w] = roomOf(ways.get(w));
            }
            if (wayRoom[i] >= 0 && wayRoom[i] == wayRoom[j]) return true;
            int k = Math.min(i, j) * ways.size() + Math.max(i, j);
            if (joined[k] != 0) return joined[k] == 1;
            Boolean open = open(level, ways.get(i), ways.get(j));
            if (open == null) return true;                                                     // not loaded: hoped open, asked again later
            joined[k] = (byte) (open ? 1 : 2);
            return open;
        }
    }

    /**
     * Where to walk next from {@code from} on the way to {@code to}: {@code to} itself when it is near and in the same
     * building (or outdoors with it), or the town has no map; else the next waypoint on the shortest way; {@code to}
     * again if the map doesn't join them.
     */
    public static BlockPos next(Map map, LevelReader level, BlockPos from, BlockPos to) {
        if (map == null || map.isEmpty()) return to;
        int ra = map.roomOf(from), rb = map.roomOf(to);
        if (ra == rb && dist(from, to) <= FAR) return to;
        List<BlockPos> route = route(map, level, from, to);
        if (route == null) return to;
        for (BlockPos p : route) if (dist(from, p) > HERE) return p;
        return to;
    }

    /** The waypoints from {@code from} to {@code to} (ending with {@code to}, not including {@code from}), or null if they don't join. */
    public static List<BlockPos> route(Map map, LevelReader level, BlockPos from, BlockPos to) {
        int n = map.ways.size(), start = n, goal = n + 1;
        BlockPos[] node = new BlockPos[n + 2];
        for (int i = 0; i < n; i++) node[i] = map.ways.get(i);
        node[start] = from;
        node[goal] = to;
        int[] room = {map.roomOf(from), map.roomOf(to)};
        double[] best = new double[n + 2];
        int[] prev = new int[n + 2];
        boolean[] done = new boolean[n + 2];
        Arrays.fill(best, Double.MAX_VALUE);
        Arrays.fill(prev, -1);
        best[start] = 0;
        while (true) {                                                                        // Dijkstra (a town has a few hundred points)
            int u = -1;
            for (int i = 0; i < node.length; i++) if (!done[i] && best[i] < Double.MAX_VALUE && (u < 0 || best[i] < best[u])) u = i;
            if (u < 0 || u == goal) break;
            done[u] = true;
            for (int v = 0; v < node.length; v++) {
                if (done[v] || v == u) continue;
                double d = dist(node[u], node[v]);
                if (best[u] + d >= best[v] || !edge(map, level, node, room, n, u, v, d)) continue;
                best[v] = best[u] + d;
                prev[v] = u;
            }
        }
        if (best[goal] == Double.MAX_VALUE) return null;
        List<BlockPos> out = new ArrayList<>();
        for (int v = goal; v != start; v = prev[v]) out.add(0, node[v]);
        return out;
    }

    /** Whether one may walk straight from node u to node v: a building's inside only by its door, outdoors over open ground. */
    private static boolean edge(Map map, LevelReader level, BlockPos[] node, int[] room, int n, int u, int v, double d) {
        boolean eu = u >= n, ev = v >= n;
        if (eu && ev) return room[0] == room[1] && (room[0] >= 0 || d <= LEG && !Boolean.FALSE.equals(open(level, node[u], node[v])));
        if (eu || ev) {
            int end = eu ? u : v, way = eu ? v : u, r = room[end - n];
            if (r >= 0) return map.rooms.get(r).door() == way || map.roomOf(node[way]) == r;   // in a building: by its door, or its own inner waypoints
            return d <= LEG && map.roomOf(node[way]) < 0 && !Boolean.FALSE.equals(open(level, node[end], node[way]));
        }
        return d <= LEG && map.joined(level, u, v);
    }

    /**
     * Whether the straight line between two spots is open ground: somewhere to stand (feet and head clear, a floor)
     * every block along it, a block up or down allowed. Null if part of it isn't loaded.
     */
    static Boolean open(LevelReader level, BlockPos a, BlockPos b) {
        int steps = (int) Math.ceil(dist(a, b));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int k = 1; k < steps; k++) {
            double t = (double) k / steps;
            int x = (int) Math.floor(a.getX() + (b.getX() - a.getX()) * t + 0.5), z = (int) Math.floor(a.getZ() + (b.getZ() - a.getZ()) * t + 0.5);
            int y = (int) Math.round(a.getY() + (b.getY() - a.getY()) * t);
            if (!level.hasChunkAt(p.set(x, y, z))) return null;
            if (!standable(level, p.set(x, y, z)) && !standable(level, p.set(x, y + 1, z)) && !standable(level, p.set(x, y - 1, z))) return false;
        }
        return true;
    }

    /** Feet and head clear (a door counts as clear: residents open them), and a floor. */
    static boolean standable(BlockGetter level, BlockPos p) {
        BlockPos below = p.below(), above = p.above();
        return clear(level, p) && clear(level, above) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private static boolean clear(BlockGetter level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof DoorBlock || s.getCollisionShape(level, p).isEmpty();
    }

    static double dist(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }
}
