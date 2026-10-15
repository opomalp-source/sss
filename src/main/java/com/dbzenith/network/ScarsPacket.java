package com.dbzenith.network;

import com.dbzenith.combat.Scars;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server to clients (the limbs rework): a fighter's battle scars. */
public record ScarsPacket(int entityId, List<Scars.Scar> scars) {
    public static void encode(ScarsPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeVarInt(m.scars.size());
        for (Scars.Scar s : m.scars) {
            buf.writeByte(s.part());
            buf.writeByte(s.side());
            buf.writeFloat(s.u());
            buf.writeFloat(s.v());
            buf.writeFloat(s.angle());
            buf.writeFloat(s.length());
            buf.writeByte(s.kind());
            buf.writeInt(s.seed());
            buf.writeLong(s.made());
        }
    }

    public static ScarsPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt(), n = Math.min(64, buf.readVarInt());
        List<Scars.Scar> out = new ArrayList<>();
        for (int i = 0; i < n; i++)
            out.add(new Scars.Scar(buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readInt(), buf.readLong()));
        return new ScarsPacket(id, out);
    }

    public static void handle(ScarsPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.render.ScarLayer.set(m.entityId(), m.scars());     // client-only class
    }
}
