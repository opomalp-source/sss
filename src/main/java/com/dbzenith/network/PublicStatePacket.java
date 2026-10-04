package com.dbzenith.network;

import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.Aura;
import com.dbzenith.race.Races;
import com.dbzenith.stats.Race;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to every client tracking a player (and the player): the visible subset of their state, used for
 * auras, forms, racial features and appearance. Sent only when it changes.
 */
public record PublicStatePacket(int entityId, int flags, int release, int auraColor, String form, int overdrive,
                                int race, int bodyType, int hairStyle, int hairColor, int eyeColor, long battlePower,
                                int looks) {
    public static final int CHARGING = 1;
    public static final int FLYING = 2;
    public static final int GUARDING = 4;
    public static final int HEAVY = 8;
    public static final int TAIL = 16;
    /** Bit in {@code looks}: show the full race skin. */
    public static final int RACE_LOOK = 256;

    public static PublicStatePacket of(int entityId, PlayerData d) {
        int flags = (d.isCharging() ? CHARGING : 0) | (d.isFlying() ? FLYING : 0)
                | (d.isGuarding() ? GUARDING : 0) | (d.isChargingHeavy() ? HEAVY : 0)
                | (Races.of(d.getRace()).tail() && d.hasTail() ? TAIL : 0);
        return new PublicStatePacket(entityId, flags, d.getReleasePercent(), Aura.color(d), d.getFormId(), d.getOverdriveLevel(),
                d.getRace().ordinal(), d.getBodyType().ordinal(), d.getHairStyle(), d.getHairColor(), d.getEyeColor(),
                d.hasFlag("god_ki") ? -1 : com.dbzenith.stats.StatCalculator.battlePower(d), // -1: god ki cannot be read
                d.getScar() | d.getTattoo() << 4 | (d.isRaceLook() ? RACE_LOOK : 0));
    }

    public int stateHash() {
        int h = (((flags * 31 + release) * 31 + auraColor) * 31 + form.hashCode()) * 31 + overdrive;
        h = ((h * 31 + race) * 31 + bodyType) * 31 + hairStyle;
        return (((h * 31 + hairColor) * 31 + eyeColor) * 31 + Long.hashCode(battlePower)) * 31 + looks;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public int scar() {
        return looks & 0xF;
    }

    public int tattoo() {
        return (looks >> 4) & 0xF;
    }

    public boolean raceLook() {
        return (looks & RACE_LOOK) != 0;
    }

    public Race raceEnum() {
        Race[] all = Race.values();
        return race >= 0 && race < all.length ? all[race] : Race.HUMAN;
    }

    public static void encode(PublicStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.flags);
        buf.writeByte(msg.release);
        buf.writeInt(msg.auraColor);
        buf.writeUtf(msg.form, 64);
        buf.writeByte(msg.overdrive);
        buf.writeByte(msg.race);
        buf.writeByte(msg.bodyType);
        buf.writeByte(msg.hairStyle);
        buf.writeInt(msg.hairColor);
        buf.writeInt(msg.eyeColor);
        buf.writeVarLong(msg.battlePower);
        buf.writeShort(msg.looks);
    }

    public static PublicStatePacket decode(FriendlyByteBuf buf) {
        return new PublicStatePacket(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readInt(), buf.readUtf(64), buf.readByte(),
                buf.readByte(), buf.readByte(), buf.readByte(), buf.readInt(), buf.readInt(), buf.readVarLong(),
                buf.readUnsignedShort());
    }

    public static void handle(PublicStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        PublicStatePacket old = ClientPublicStates.get(msg.entityId);
        ClientPublicStates.put(msg);
        if (old == null || !old.form.equals(msg.form)) com.dbzenith.client.ClientHooks.refreshDimensions(msg.entityId);
    }
}
