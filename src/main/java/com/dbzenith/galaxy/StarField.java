package com.dbzenith.galaxy;

import com.dbzenith.DBZenith;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.StarFieldPacket;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * The stars in the sky are the catalogue's systems (the space rework, phase 2). Every system within {@link #RADIUS}
 * catalogue units of the observer's system is a star point: its position, its star types and multiplicity come
 * from {@link GalaxyCatalog#head}, the same draws as the atlas and travel. The client cannot compute these (it
 * never gets the world seed), so the server streams them once per system change ({@link StarFieldPacket}: relative
 * positions and star types only, no seeds, no planets). Computed on a worker thread, at most {@link #CACHE} fields
 * cached per server. Drawing the sky never creates a dimension, terrain or discovery record.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class StarField {
    private StarField() {}

    /** How far the naked eye reaches, in catalogue units (six sectors: about 7,000 systems). */
    public static final double RADIUS = 6000;
    /** Fields kept per server (LRU). */
    public static final int CACHE = 8;
    /** The most stars a field may hold (a safety bound; about 7,200 is typical). */
    public static final int MAX = 16000;

    /** A star of the field: its catalogue head and its offset from the observer's system (catalogue units). */
    public record Entry(GalaxyCatalog.Head head, double dx, double dy, double dz) {}

    private static final class State {
        final Map<GalaxyCatalog.Id, CompletableFuture<List<Entry>>> cache = new LinkedHashMap<>(16, .75f, true);
        final Map<UUID, GalaxyCatalog.Id> sent = new HashMap<>();
    }

    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

    private static synchronized State state(MinecraftServer s) {
        return STATES.computeIfAbsent(s, k -> new State());
    }

    /** Every catalogue system within {@link #RADIUS} of {@code center}, nearest first (excluding the centre itself). */
    public static List<Entry> near(long seed, long salt, GalaxyCatalog.Id center) {
        var here = GalaxyCatalog.head(seed, salt, center);
        int reach = (int) Math.ceil(RADIUS / 1000) + 1;
        List<Entry> out = new ArrayList<>();
        for (int x = center.x() - reach; x <= center.x() + reach; x++)
            for (int y = center.y() - reach; y <= center.y() + reach; y++)
                for (int z = center.z() - reach; z <= center.z() + reach; z++) {
                    if (Math.abs(x) > GalaxyCatalog.LIMIT || Math.abs(y) > GalaxyCatalog.LIMIT || Math.abs(z) > GalaxyCatalog.LIMIT) continue;
                    // a sector's cube must reach within the radius before its eight systems are drawn
                    double nx = clamp(here.x(), x * 1000d, x * 1000d + 1000) - here.x(), ny = clamp(here.y(), y * 1000d, y * 1000d + 1000) - here.y(), nz = clamp(here.z(), z * 1000d, z * 1000d + 1000) - here.z();
                    if (nx * nx + ny * ny + nz * nz > RADIUS * RADIUS) continue;
                    for (int slot = 0; slot <= GalaxyCatalog.SYSTEMS; slot++) {
                        if (slot == GalaxyCatalog.SYSTEMS && !IconicSystems.reserved(x, y, z, slot)) continue;
                        var id = new GalaxyCatalog.Id(x, y, z, slot);
                        if (id.equals(center)) continue;
                        var h = GalaxyCatalog.head(seed, salt, id);
                        double dx = h.x() - here.x(), dy = h.y() - here.y(), dz = h.z() - here.z();
                        if (dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS) out.add(new Entry(h, dx, dy, dz));
                    }
                }
        out.sort(Comparator.comparingDouble(e -> e.dx * e.dx + e.dy * e.dy + e.dz * e.dz));
        return out.size() > MAX ? List.copyOf(out.subList(0, MAX)) : List.copyOf(out);
    }

    /** The field around a system, computed once on a worker thread and cached. */
    public static CompletableFuture<List<Entry>> field(MinecraftServer server, GalaxyCatalog.Id center) {
        State s = state(server);
        long seed = server.overworld().getSeed(), salt = GalaxyData.get(server).salt();
        synchronized (s) {
            var f = s.cache.get(center);
            if (f != null && !f.isCompletedExceptionally()) return f;
            f = CompletableFuture.supplyAsync(() -> near(seed, salt, center), Util.backgroundExecutor());
            s.cache.put(center, f);
            while (s.cache.size() > CACHE) s.cache.remove(s.cache.keySet().iterator().next());
            return f;
        }
    }

    /** Is {@code target} a star of {@code center}'s sky (a cheap check: two heads)? */
    public static boolean visible(MinecraftServer server, GalaxyCatalog.Id center, GalaxyCatalog.Id target) {
        if (center.equals(target)) return true;
        long seed = server.overworld().getSeed(), salt = GalaxyData.get(server).salt();
        var a = GalaxyCatalog.head(seed, salt, center);
        var b = GalaxyCatalog.head(seed, salt, target);
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS;
    }

    /** Sends the player the stars of their current system's sky, unless they already have them. */
    public static void sync(ServerPlayer p) {
        var center = GalaxyData.current(p);
        State s = state(p.server);
        synchronized (s) {
            if (center.equals(s.sent.get(p.getUUID()))) return;
            s.sent.put(p.getUUID(), center);
        }
        var server = p.server;
        var uuid = p.getUUID();
        field(server, center).whenComplete((list, error) -> server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return;
            if (error != null) {
                DBZenith.LOGGER.error("Star field for {} failed", center, error);
                synchronized (s) { s.sent.remove(uuid); }
                return;
            }
            if (!center.equals(GalaxyData.current(player))) return;      // moved on meanwhile; that move synced its own sky
            var wp = GalaxyData.get(server).waypoint(uuid);
            ModNetwork.sendTo(player, StarFieldPacket.of(center, wp == null ? null : wp.system(), list));
        }));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            State s = state(p.server);
            synchronized (s) { s.sent.remove(p.getUUID()); }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        synchronized (StarField.class) { STATES.remove(e.getServer()); }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
