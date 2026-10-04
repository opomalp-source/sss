package com.dbzenith.network;

import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.Aura;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to every client tracking a player (and the player): the visible subset of their state,
 * used for auras and (Phase 2) forms. Sent only when it changes.
 */
public record PublicStatePacket(int entityId, int flags, int release, int auraColor, String form, int overdrive) {
    public static final int CHARGING = 1;
    public static final int FLYING = 2;
    public static final int GUARDING = 4;
    public static final int HEAVY = 8;

    public static PublicStatePacket of(int entityId, PlayerData d) {
        int flags = (d.isCharging() ? CHARGING : 0) | (d.isFlying() ? FLYING : 0)
                | (d.isGuarding() ? GUARDING : 0) | (d.isChargingHeavy() ? HEAVY : 0);
        return new PublicStatePacket(entityId, flags, d.getReleasePercent(), Aura.color(d), d.getFormId(), d.getOverdriveLevel());
    }

    public int stateHash() {
        return (((flags * 31 + release) * 31 + auraColor) * 31 + form.hashCode()) * 31 + overdrive;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public static void encode(PublicStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.flags);
        buf.writeByte(msg.release);
        buf.writeInt(msg.auraColor);
        buf.writeUtf(msg.form, 64);
        buf.writeByte(msg.overdrive);
    }

    public static PublicStatePacket decode(FriendlyByteBuf buf) {
        return new PublicStatePacket(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readInt(), buf.readUtf(64), buf.readByte());
    }

    public static void handle(PublicStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        PublicStatePacket old = ClientPublicStates.get(msg.entityId);
        ClientPublicStates.put(msg);
        if (old == null || !old.form.equals(msg.form)) com.dbzenith.client.ClientHooks.refreshDimensions(msg.entityId); // client thread; ClientPublicStates has no client-only imports
    }
}
