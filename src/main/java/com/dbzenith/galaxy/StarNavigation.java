package com.dbzenith.galaxy;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.network.InputGuard;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.StarSelectPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Targeting a star of the sky (the space rework, phase 2). The server accepts only a system that is really a star of
 * the player's sky ({@link StarField#visible}), records it as observed, and replies with what can be read of it from
 * here: its name, star types, multiplicity, distance and the player's state for it. Planets, inhabitants and
 * resources stay unknown until the system is charted (visited, or surveyed by anyone landing there). Targeting
 * creates no dimension, terrain or discovery.
 */
public final class StarNavigation {
    private StarNavigation() {}

    public static void request(ServerPlayer p, String raw, int mode) {
        if (!InputGuard.allow(p, InputGuard.Kind.GALAXY)) return;
        if (mode == StarSelectPacket.CLEAR) {
            ModNetwork.sendTo(p, new StarSelectPacket.Info(new CompoundTag()));
            return;
        }
        GalaxyCatalog.Id id;
        try {
            id = GalaxyCatalog.Id.parse(raw);
        } catch (IllegalArgumentException ex) {
            InputGuard.sane(p, false, "star target");
            return;
        }
        if (mode < 0 || mode > StarSelectPacket.MAP || !StarField.visible(p.server, GalaxyData.current(p), id)) {
            ModNetwork.sendTo(p, new StarSelectPacket.Info(new CompoundTag()));
            return;
        }
        var data = GalaxyData.get(p.server);
        data.observe(p.getUUID(), id);
        if (mode == StarSelectPacket.WAYPOINT) {
            data.waypoint(p.getUUID(), new GalaxyCatalog.Ref(id, 0));
            p.displayClientMessage(Component.literal("Waypoint set: " + info(p, id).getString("name")), true);
        }
        if (mode == StarSelectPacket.MAP) GalaxyService.openAt(p, id);
        ModNetwork.sendTo(p, new StarSelectPacket.Info(info(p, id)));
    }

    /** What the player can read of a star from where they are (its state decides whether planets are known). */
    public static CompoundTag info(ServerPlayer p, GalaxyCatalog.Id id) {
        var server = p.server;
        var data = GalaxyData.get(server);
        long seed = server.overworld().getSeed();
        var head = GalaxyCatalog.head(seed, data.salt(), id);
        var here = GalaxyCatalog.head(seed, data.salt(), GalaxyData.current(p));
        boolean divine = id.equals(GalaxyCatalog.BEERUS) && !p.isCreative() && !ModCapabilities.getOrThrow(p).hasFlag("god_ki");
        CompoundTag t = new CompoundTag();
        t.putString("id", id.toString());
        t.putString("name", divine ? "An unreadable star" : head.name());
        ListTag types = new ListTag();
        for (var s : head.stars()) types.add(StringTag.valueOf(GalaxyCatalog.label(s.type())));
        t.put("types", types);
        t.putInt("color", head.stars().get(0).type().color);
        t.putInt("stars", head.stars().size());
        t.putDouble("distance", Math.sqrt(Math.pow(head.x() - here.x(), 2) + Math.pow(head.y() - here.y(), 2) + Math.pow(head.z() - here.z(), 2)));
        String state = GalaxyService.state(p, data, id);
        t.putString("state", state);
        if (GalaxyService.CHARTED.equals(state)) t.putInt("planets", GalaxyCatalog.system(seed, data.salt(), id).planets().size());
        var wp = data.waypoint(p.getUUID());
        t.putBoolean("waypoint", wp != null && wp.system().equals(id));
        return t;
    }
}
