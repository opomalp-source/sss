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
                                int looks, String hairCode, int skinTone, int height, int variant, String transformTarget) {
    public static final int CHARGING = 1;
    public static final int FLYING = 2;
    public static final int GUARDING = 4;
    public static final int HEAVY = 8;
    public static final int TAIL = 16;
    public static final int MEDITATING = 32;
    /** Powering up into a form ({@link #transformTarget}). */
    public static final int TRANSFORMING = 64;
    public static final int KAIOKEN = 128;
    /** A Ki Barrier is up. */
    public static final int BARRIER = 256;
    /** Bit in {@code looks}: show the full race skin. */
    public static final int RACE_LOOK = 256;

    public static PublicStatePacket of(int entityId, PlayerData d) {
        int flags = (d.isCharging() ? CHARGING : 0) | (d.isFlying() ? FLYING : 0)
                | (d.isGuarding() ? GUARDING : 0) | (d.isChargingHeavy() ? HEAVY : 0)
                | (Races.of(d.getRace()).tail() && d.hasTail() ? TAIL : 0) | (d.isMeditating() ? MEDITATING : 0) | (d.isTransforming() ? TRANSFORMING : 0)
                | (d.getKaiokenStage() > 0 ? KAIOKEN : 0) | (d.getRacialActive().contains("ki_barrier") ? BARRIER : 0);
        return new PublicStatePacket(entityId, flags, d.getReleasePercent(), Aura.color(d), d.getFormId(), d.getOverdriveLevel(),
                d.getRace().ordinal(), d.getBodyType().ordinal(), d.getHairStyle(), d.getHairColor(), d.getEyeColor(),
                d.hasFlag("god_ki") ? -1 : com.dbzenith.stats.StatCalculator.battlePower(d), // -1: god ki cannot be read
                d.getScar() | d.getTattoo() << 4 | (d.isRaceLook() ? RACE_LOOK : 0),
                d.getHairCode(), d.getSkinTone(), d.getHeightPercent(), d.getVariant().ordinal(), d.getTransformTarget());
    }

    public int stateHash() {
        int h = (((flags * 31 + release) * 31 + auraColor) * 31 + form.hashCode()) * 31 + overdrive;
        h = ((h * 31 + race) * 31 + bodyType) * 31 + hairStyle;
        h = (((h * 31 + hairColor) * 31 + eyeColor) * 31 + Long.hashCode(battlePower)) * 31 + looks;
        return ((((h * 31 + hairCode.hashCode()) * 31 + skinTone) * 31 + height) * 31 + variant) * 31 + transformTarget.hashCode();
    }

    /** A copy with a different look (client previews in the creation, barber and Life screens). */
    public PublicStatePacket withAppearance(String hairCode, int hairColor, int eyeColor, int skinTone) {
        return new PublicStatePacket(entityId, flags, release, auraColor, form, overdrive, race, bodyType, hairStyle, hairColor,
                eyeColor, battlePower, looks, hairCode, skinTone, height, variant, transformTarget);
    }

    /** Charging, winding up a heavy or powering into a form: the aura roars. */
    public boolean powering() {
        return (flags & (CHARGING | HEAVY | TRANSFORMING)) != 0;
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

    public com.dbzenith.race.Variant variantEnum() {
        com.dbzenith.race.Variant[] all = com.dbzenith.race.Variant.values();
        return variant >= 0 && variant < all.length && all[variant].race() == raceEnum() ? all[variant] : com.dbzenith.race.Variant.defaultFor(raceEnum());
    }

    public Race raceEnum() {
        Race[] all = Race.values();
        return race >= 0 && race < all.length ? all[race] : Race.HUMAN;
    }

    public static void encode(PublicStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.flags);
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
        buf.writeUtf(msg.hairCode, com.dbzenith.appearance.HairCode.MAX_CODE_LENGTH);
        buf.writeInt(msg.skinTone);
        buf.writeByte(msg.height);
        buf.writeByte(msg.variant);
        buf.writeUtf(msg.transformTarget, 64);
    }

    public static PublicStatePacket decode(FriendlyByteBuf buf) {
        return new PublicStatePacket(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readInt(), buf.readUtf(64), buf.readByte(),
                buf.readByte(), buf.readByte(), buf.readByte(), buf.readInt(), buf.readInt(), buf.readVarLong(),
                buf.readUnsignedShort(), buf.readUtf(com.dbzenith.appearance.HairCode.MAX_CODE_LENGTH), buf.readInt(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUtf(64));
    }

    public static void handle(PublicStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        PublicStatePacket old = ClientPublicStates.get(msg.entityId);
        ClientPublicStates.put(msg);
        if (old == null || !old.form.equals(msg.form) || old.height != msg.height) com.dbzenith.client.ClientHooks.refreshDimensions(msg.entityId);
    }
}
