package com.dbzenith.client;

import com.dbzenith.network.PublicStatePacket;

import java.util.HashMap;
import java.util.Map;

/** Visible state of players this client can see, keyed by entity id. */
public final class ClientPublicStates {
    private static final Map<Integer, PublicStatePacket> STATES = new HashMap<>();

    private ClientPublicStates() {}

    public static void put(PublicStatePacket state) {
        STATES.put(state.entityId(), state);
    }

    public static PublicStatePacket get(int entityId) {
        return STATES.get(entityId);
    }

    public static void clear() {
        STATES.clear();
    }
}
