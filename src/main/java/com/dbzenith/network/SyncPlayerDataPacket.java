package com.dbzenith.network;

import com.dbzenith.client.ClientPlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to owning client: the player's full Dragon Block state plus derived stats. */
public record SyncPlayerDataPacket(CompoundTag tag) {

    public static void encode(SyncPlayerDataPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.tag);
    }

    public static SyncPlayerDataPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new SyncPlayerDataPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(SyncPlayerDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // consumerMainThread runs this on the client thread. ClientPlayerData has no client-only imports, so this is dist-safe.
        ClientPlayerData.apply(msg.tag);
    }
}
