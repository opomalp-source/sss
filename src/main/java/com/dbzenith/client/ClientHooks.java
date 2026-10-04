package com.dbzenith.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Client-only helpers called from packet handlers (only ever executed on the client). */
public final class ClientHooks {
    private ClientHooks() {}

    private static boolean creationPrompted;

    /**
     * After a sync: open character creation once per session if this character has not been created yet.
     * Skipped for the scripted dev client so it does not cover the screenshots (it opens it via /dbz devshot create_).
     */
    public static void maybeOpenCreation() {
        Minecraft mc = Minecraft.getInstance();
        if (creationPrompted || Boolean.getBoolean("dbzenith.devAutomation")) return;
        if (mc.player == null || !ClientPlayerData.hasData() || ClientPlayerData.get().isCharacterCreated() || mc.screen != null) return;
        creationPrompted = true;
        mc.setScreen(new com.dbzenith.client.screen.CharacterCreationScreen());
    }

    public static void resetCreationPrompt() {
        creationPrompted = false;
    }

    /** A player changed form: recompute their hitbox and eye height (giant forms). */
    public static void refreshDimensions(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e != null) e.refreshDimensions();
    }
}
