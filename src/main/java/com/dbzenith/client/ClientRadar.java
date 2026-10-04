package com.dbzenith.client;

import com.dbzenith.network.RadarPacket;

import java.util.List;

/** Latest Dragon Radar blips from the server. No client-only imports (filled from a packet handler). */
public final class ClientRadar {
    private static List<RadarPacket.Blip> blips = List.of();
    private static long receivedAtMillis;

    private ClientRadar() {}

    public static void update(List<RadarPacket.Blip> b) {
        blips = List.copyOf(b);
        receivedAtMillis = System.currentTimeMillis();
    }

    /** Blips, or nothing if the last update is older than 5 s (stopped holding the radar). */
    public static List<RadarPacket.Blip> blips() {
        return System.currentTimeMillis() - receivedAtMillis > 5000 ? List.of() : blips;
    }

    public static void clear() {
        blips = List.of();
    }
}
