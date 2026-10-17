package com.dbzenith.network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
/** S2C only: current environment, loaded-world scan summary or a landing card. Never terrain seeds. */
public record PlanetActivityPacket(CompoundTag data){
    public static void encode(PlanetActivityPacket m,FriendlyByteBuf b){b.writeNbt(m.data);}
    public static PlanetActivityPacket decode(FriendlyByteBuf b){var t=b.readNbt();return new PlanetActivityPacket(t==null?new CompoundTag():t);}
    public static void handle(PlanetActivityPacket m,Supplier<NetworkEvent.Context> c){com.dbzenith.client.space.PlanetExperience.receive(m.data);}
}
