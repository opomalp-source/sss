package com.dbzenith.npc;

/**
 * Optional hints an entity can give the client's motion engine (CX-18) about things it cannot work out from movement
 * alone. Everything else (walking, sprinting, jumping, falling, hovering, flight, landing, hits) is read from the entity
 * itself, so most NPCs need nothing. Implement on the entity class; read on the client, so back each answer with
 * synced entity data.
 */
public interface Animated {
    /** Flying on purpose (the engine also spots anything that hangs in the air without falling). */
    default boolean isFlyingNow() {
        return false;
    }

    /** Powering up: the charge pose. */
    default boolean isChargingKi() {
        return false;
    }
}
