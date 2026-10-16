package com.dbzenith.network;

import com.dbzenith.galaxy.GalaxyService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public final class GalaxyPackets {
    private GalaxyPackets() {}
    public record Request(int sequence,int action,String id,int planet,String query,int cursor) {
        public static void encode(Request m,FriendlyByteBuf b) { b.writeVarInt(m.sequence);b.writeVarInt(m.action);b.writeUtf(m.id,40);b.writeInt(m.planet);b.writeUtf(m.query,32);b.writeVarInt(m.cursor); }
        public static Request decode(FriendlyByteBuf b) { return new Request(b.readVarInt(),b.readVarInt(),b.readUtf(40),b.readInt(),b.readUtf(32),b.readVarInt()); }
        public static void handle(Request m,Supplier<NetworkEvent.Context> c) { var p=c.get().getSender();if(p!=null)GalaxyService.request(p,m); }
    }
    public record Data(int sequence,boolean open,CompoundTag data) {
        public static void encode(Data m,FriendlyByteBuf b) { b.writeVarInt(m.sequence);b.writeBoolean(m.open);b.writeNbt(m.data); }
        public static Data decode(FriendlyByteBuf b) { int seq=b.readVarInt();boolean open=b.readBoolean();var t=b.readNbt();return new Data(seq,open,t==null?new CompoundTag():t); }
        public static void handle(Data m,Supplier<NetworkEvent.Context> c) { com.dbzenith.client.screen.GalaxyScreen.receive(m); }
    }
}
