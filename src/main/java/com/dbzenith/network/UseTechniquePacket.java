package com.dbzenith.network;

import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: "I want to use this technique". Validated by {@link TechniqueHandler}. */
public record UseTechniquePacket(String techniqueId) {
    public static void encode(UseTechniquePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.techniqueId, 64);
    }

    public static UseTechniquePacket decode(FriendlyByteBuf buf) {
        return new UseTechniquePacket(buf.readUtf(64));
    }

    public static void handle(UseTechniquePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        TechniqueHandler.Result r = TechniqueHandler.use(player, Techniques.resolve(com.dbzenith.data.ModCapabilities.get(player).orElse(null), msg.techniqueId));
        if (r == TechniqueHandler.Result.STUNNED) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.stunned"), true);
        if (r == TechniqueHandler.Result.SEALED) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.ki_sealed"), true);
    }
}
