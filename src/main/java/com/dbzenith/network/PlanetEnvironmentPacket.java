package com.dbzenith.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record PlanetEnvironmentPacket(String dimension,String name,double gravity,int atmosphere,int radius,int air,String protection){
    public static void encode(PlanetEnvironmentPacket m,FriendlyByteBuf b){b.writeUtf(m.dimension,160);b.writeUtf(m.name,80);b.writeDouble(m.gravity);b.writeVarInt(m.atmosphere);b.writeVarInt(m.radius);b.writeVarInt(m.air);b.writeUtf(m.protection,32);}
    public static PlanetEnvironmentPacket decode(FriendlyByteBuf b){return new PlanetEnvironmentPacket(b.readUtf(160),b.readUtf(80),b.readDouble(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(32));}
    public static void handle(PlanetEnvironmentPacket m,Supplier<NetworkEvent.Context> c){com.dbzenith.client.ClientPlanet.receive(m);}
}
