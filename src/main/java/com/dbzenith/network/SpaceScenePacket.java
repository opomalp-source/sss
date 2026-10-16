package com.dbzenith.network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
/** S2C only. Reuses bounded survey descriptors; no terrain seed, geometry authority or dimension creation on clients. */
public record SpaceScenePacket(CompoundTag data){
    public static void encode(SpaceScenePacket m,FriendlyByteBuf b){b.writeNbt(m.data);}
    public static SpaceScenePacket decode(FriendlyByteBuf b){var t=b.readNbt();return new SpaceScenePacket(t==null?new CompoundTag():t);}
    public static void handle(SpaceScenePacket m,Supplier<NetworkEvent.Context> c){com.dbzenith.client.space.SpaceClient.receive(m.data);}
}
