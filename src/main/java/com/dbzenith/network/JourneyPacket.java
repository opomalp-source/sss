package com.dbzenith.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record JourneyPacket(boolean active,int elapsed,int duration,String name){
    public static void encode(JourneyPacket m,FriendlyByteBuf b){b.writeBoolean(m.active);b.writeVarInt(m.elapsed);b.writeVarInt(m.duration);b.writeUtf(m.name,64);}
    public static JourneyPacket decode(FriendlyByteBuf b){return new JourneyPacket(b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readUtf(64));}
    public static void handle(JourneyPacket m,Supplier<NetworkEvent.Context> c){com.dbzenith.client.space.JourneyClient.receive(m);}
}
