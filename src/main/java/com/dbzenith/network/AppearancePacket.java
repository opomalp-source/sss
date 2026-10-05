package com.dbzenith.network;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.data.PlayerDataEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: restyle after creation (the barber). Hair code and colours and skin tone are cosmetic and can
 * change at any time; height and body type are fixed at creation because they touch the hitbox and stats.
 */
public record AppearancePacket(String hairCode, int hairColor, int eyeColor, int skinTone) {
    public static void encode(AppearancePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.hairCode, HairCode.MAX_CODE_LENGTH);
        buf.writeInt(msg.hairColor);
        buf.writeInt(msg.eyeColor);
        buf.writeInt(msg.skinTone);
    }

    public static AppearancePacket decode(FriendlyByteBuf buf) {
        return new AppearancePacket(buf.readUtf(HairCode.MAX_CODE_LENGTH), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(AppearancePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ModCapabilities.get(player).ifPresent(d -> {
            if (apply(d, msg)) PlayerDataEvents.sync(player);
        });
    }

    /** Applies the restyle; false (nothing changed) before a character exists or for a malformed hair code. */
    public static boolean apply(PlayerData d, AppearancePacket msg) {
        if (!d.isCharacterCreated() || HairCode.sanitize(msg.hairCode) == null) return false;
        d.setHairCode(msg.hairCode);
        d.setHairColor(msg.hairColor);
        d.setEyeColor(msg.eyeColor);
        d.setSkinTone(msg.skinTone);
        return true;
    }
}
