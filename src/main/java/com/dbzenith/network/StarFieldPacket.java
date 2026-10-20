package com.dbzenith.network;

import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.StarField;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * The stars of the observer's sky (the space rework, phase 2; {@link StarField}): for each catalogue system in
 * range, its sector offset and slot (its identity), its position relative to the observer (catalogue units, float), and its star types. About 18 bytes a star; no names, seeds or planets.
 */
public record StarFieldPacket(String center, String waypoint, int count, byte[] sectors, float[] positions, short[] stars) {
    /** Most stars a packet may carry (the decoder refuses more). */
    public static final int LIMIT = StarField.MAX;

    public static StarFieldPacket of(GalaxyCatalog.Id center, GalaxyCatalog.Id waypoint, List<StarField.Entry> entries) {
        int n = Math.min(entries.size(), LIMIT);
        byte[] sectors = new byte[n * 4];
        float[] positions = new float[n * 3];
        short[] stars = new short[n];
        for (int i = 0; i < n; i++) {
            var e = entries.get(i);
            var id = e.head().id();
            sectors[i * 4] = (byte) (id.x() - center.x());
            sectors[i * 4 + 1] = (byte) (id.y() - center.y());
            sectors[i * 4 + 2] = (byte) (id.z() - center.z());
            sectors[i * 4 + 3] = (byte) id.slot();
            positions[i * 3] = (float) e.dx();
            positions[i * 3 + 1] = (float) e.dy();
            positions[i * 3 + 2] = (float) e.dz();
            stars[i] = types(e.head().stars());
        }
        return new StarFieldPacket(center.toString(), waypoint == null ? "" : waypoint.toString(), n, sectors, positions, stars);
    }

    /** Star count (2 bits) and up to three star types (3 bits each). */
    public static short types(List<GalaxyCatalog.Star> stars) {
        int v = Math.min(3, stars.size()) << 9;
        for (int i = 0; i < Math.min(3, stars.size()); i++) v |= stars.get(i).type().ordinal() << (i * 3);
        return (short) v;
    }

    public static void encode(StarFieldPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.center, 40);
        b.writeUtf(m.waypoint, 40);
        b.writeVarInt(m.count);
        b.writeBytes(m.sectors, 0, m.count * 4);
        for (int i = 0; i < m.count * 3; i++) b.writeFloat(m.positions[i]);
        for (int i = 0; i < m.count; i++) b.writeShort(m.stars[i]);
    }

    public static StarFieldPacket decode(FriendlyByteBuf b) {
        String center = b.readUtf(40), waypoint = b.readUtf(40);
        int n = b.readVarInt();
        if (n < 0 || n > LIMIT) throw new IllegalArgumentException("Star field too large: " + n);
        byte[] sectors = new byte[n * 4];
        b.readBytes(sectors);
        float[] positions = new float[n * 3];
        short[] stars = new short[n];
        for (int i = 0; i < n * 3; i++) positions[i] = b.readFloat();
        for (int i = 0; i < n; i++) stars[i] = b.readShort();
        return new StarFieldPacket(center, waypoint, n, sectors, positions, stars);
    }

    public static void handle(StarFieldPacket m, Supplier<NetworkEvent.Context> c) {
        com.dbzenith.client.space.StarSky.receive(m);
    }
}
