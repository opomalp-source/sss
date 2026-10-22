package com.dbzenith.npc;

/**
 * An NPC whose look varies by entity (town residents): the skin name its renderer draws (a fighter skin, with its 3D
 * parts from {@code NpcLooks}). Read on the client, so back it with synced entity data.
 */
public interface SkinVariant {
    String skin();
}
