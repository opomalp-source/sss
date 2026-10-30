package com.dbzenith.network;

import com.dbzenith.dragonball.DragonSpiritEntity;
import com.dbzenith.dragonball.Wish;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the wish. Only the summoner of a nearby, unspent dragon can make it. */
public record MakeWishPacket(int dragonId, Wish wish) {
    public static void encode(MakeWishPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.dragonId);
        buf.writeEnum(msg.wish);
    }

    public static MakeWishPacket decode(FriendlyByteBuf buf) {
        return new MakeWishPacket(buf.readVarInt(), buf.readEnum(Wish.class));
    }

    public static void handle(MakeWishPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        if (player.level().getEntity(msg.dragonId) instanceof DragonSpiritEntity dragon && dragon.distanceToSqr(player) < 96 * 96) {
            if (dragon.grant(player, msg.wish)) {
                String key = dragon.wishesLeft() > 0 ? "message.dbzenith.wish_granted_more"          // Porunga waits for the next
                        : dragon.set() == com.dbzenith.dragonball.BallSet.NAMEK ? "message.dbzenith.wish_granted_namek" : "message.dbzenith.wish_granted";
                player.server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.translatable(
                        key, player.getDisplayName(), net.minecraft.network.chat.Component.translatable(msg.wish.translationKey()), dragon.wishesLeft()), false);
            }
        }
    }
}
