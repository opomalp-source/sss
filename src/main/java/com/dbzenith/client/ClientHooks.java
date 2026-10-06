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

    /** A quest giver was right-clicked. */
    public static void openJudgement(boolean dead, int secondsLeft) {
        Minecraft.getInstance().setScreen(new com.dbzenith.client.screen.JudgementScreen(dead, secondsLeft));
    }

    public static void openQuestScreen(com.dbzenith.quest.Quest.Giver giver) {
        Minecraft.getInstance().setScreen(new com.dbzenith.client.screen.QuestScreen(giver));
    }

    /** The Space Pod was used. */
    public static void openPlanetScreen() {
        Minecraft.getInstance().setScreen(new com.dbzenith.client.screen.PlanetScreen());
    }

    /** The dragon asks for a wish. */
    public static void openWishScreen(int dragonId) {
        Minecraft.getInstance().setScreen(new com.dbzenith.client.screen.WishScreen(dragonId));
    }

    /** The first milestone: choose a path (only if nothing else is open; the Training screen offers it too). */
    public static void openTransmission(java.util.List<com.dbzenith.network.RacialPackets.Destination> list) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new com.dbzenith.client.screen.TransmissionScreen(list));
    }

    public static void openPathChoice() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null) mc.setScreen(new com.dbzenith.client.screen.PathChoiceScreen(null));
    }

    /** A player changed form: recompute their hitbox and eye height (giant forms). */
    public static void refreshDimensions(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e != null) e.refreshDimensions();
    }
}
