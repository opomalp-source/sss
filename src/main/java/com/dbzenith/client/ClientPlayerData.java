package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.data.PlayerData;
import net.minecraft.nbt.CompoundTag;

/**
 * Client-side mirror of the local player's {@link PlayerData}, filled only by server sync packets.
 * Kept separate from the entity so it survives the client player object being recreated on respawn.
 */
public final class ClientPlayerData {
    private static final boolean DEBUG_SYNC = Boolean.getBoolean("dbzenith.debugSync");

    private static PlayerData data = new PlayerData();
    private static boolean received;

    private ClientPlayerData() {}

    public static void apply(CompoundTag tag) {
        data.readSyncTag(tag);
        received = true;
        if (DEBUG_SYNC) DBZenith.LOGGER.info("[sync] received player data: {}", tag);
    }

    public static PlayerData get() {
        return data;
    }

    public static boolean hasData() {
        return received;
    }

    public static void clear() {
        data = new PlayerData();
        received = false;
    }
}
