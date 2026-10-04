package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.data.PlayerDataEvents;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the first-join character choices. Applied once; later requests are ignored. */
public record CreateCharacterPacket(CharacterCreation.Choices choices) {
    public static void encode(CreateCharacterPacket msg, FriendlyByteBuf buf) {
        CharacterCreation.Choices c = msg.choices;
        buf.writeEnum(c.race());
        buf.writeEnum(c.path());
        buf.writeEnum(c.body());
        buf.writeVarInt(c.hairStyle());
        buf.writeInt(c.hairColor());
        buf.writeInt(c.eyeColor());
        buf.writeVarInt(c.alignment());
    }

    public static CreateCharacterPacket decode(FriendlyByteBuf buf) {
        return new CreateCharacterPacket(new CharacterCreation.Choices(buf.readEnum(Race.class), buf.readEnum(FightingPath.class),
                buf.readEnum(PlayerData.BodyType.class), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readVarInt()));
    }

    public static void handle(CreateCharacterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ModCapabilities.get(player).ifPresent(d -> {
            if (CharacterCreation.create(d, msg.choices)) PlayerDataEvents.sync(player);
        });
    }
}
