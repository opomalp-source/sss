package com.dbzenith.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Client-only helpers called from packet handlers (only ever executed on the client). */
public final class ClientHooks {
    private ClientHooks() {}

    /** A player changed form: recompute their hitbox and eye height (giant forms). */
    public static void refreshDimensions(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e != null) e.refreshDimensions();
    }
}
