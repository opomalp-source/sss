package com.dbzenith.network;

import com.dbzenith.client.ClientRadar;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server to a radar holder: Dragon Ball positions (x/z only, like the radar screen) within range. */
public record RadarPacket(List<Blip> blips) {
    public record Blip(int star, int x, int z) {}

    public static void encode(RadarPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.blips.size());
        for (Blip b : msg.blips) {
            buf.writeByte(b.star);
            buf.writeVarInt(b.x);
            buf.writeVarInt(b.z);
        }
    }

    public static RadarPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 64);
        List<Blip> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new Blip(buf.readByte(), buf.readVarInt(), buf.readVarInt()));
        return new RadarPacket(list);
    }

    public static void handle(RadarPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ClientRadar.update(msg.blips); // ClientRadar has no client-only imports
    }
}
