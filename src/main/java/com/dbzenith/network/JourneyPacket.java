package com.dbzenith.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
/** A hyperspeed trip's progress; {@code dx, dy, dz}: the course from the departure system to the destination, in light years (the space rework, phase 5). */
public record JourneyPacket(boolean active,int elapsed,int duration,String name,float dx,float dy,float dz){
    public JourneyPacket(boolean active,int elapsed,int duration,String name){this(active,elapsed,duration,name,0,0,0);}
    public static void encode(JourneyPacket m,FriendlyByteBuf b){b.writeBoolean(m.active);b.writeVarInt(m.elapsed);b.writeVarInt(m.duration);b.writeUtf(m.name,64);b.writeFloat(m.dx);b.writeFloat(m.dy);b.writeFloat(m.dz);}
    public static JourneyPacket decode(FriendlyByteBuf b){return new JourneyPacket(b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readUtf(64),b.readFloat(),b.readFloat(),b.readFloat());}
    public static void handle(JourneyPacket m,Supplier<NetworkEvent.Context> c){com.dbzenith.client.space.JourneyClient.receive(m);}
}
