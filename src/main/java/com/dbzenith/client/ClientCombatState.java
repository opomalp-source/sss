package com.dbzenith.client;

import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-only UI state: which deck slot is selected and predicted cooldowns for the HUD.
 * The server stays authoritative; a mispredicted cooldown only affects the HUD bar.
 */
public final class ClientCombatState {
    private static int selected;
    private static final Map<String, long[]> cooldowns = new HashMap<>(); // id -> {start, end}

    private ClientCombatState() {}

    private static List<String> deck() {
        return ClientPlayerData.get().deckView();
    }

    /** The technique in the selected deck slot, or null if the deck is empty. */
    public static Technique selected() {
        List<String> deck = deck();
        if (deck.isEmpty()) return null;
        return Techniques.byId(deck.get(Math.floorMod(selected, deck.size())));
    }

    public static int selectedSlot() {
        List<String> deck = deck();
        return deck.isEmpty() ? 0 : Math.floorMod(selected, deck.size());
    }

    public static Technique cycle() {
        List<String> deck = deck();
        if (deck.isEmpty()) return null;
        selected = Math.floorMod(selected + 1, deck.size());
        return selected();
    }

    /** Select a deck slot directly (radial menu). */
    public static Technique select(int slot) {
        List<String> deck = deck();
        if (deck.isEmpty()) return null;
        selected = Math.floorMod(slot, deck.size());
        return selected();
    }

    public static boolean onCooldown(Technique t, long now) {
        long[] cd = cooldowns.get(t.id());
        return cd != null && now < cd[1];
    }

    public static void startCooldown(Technique t, long now) {
        cooldowns.put(t.id(), new long[]{now, now + t.cooldownTicks()});
    }

    /** 0 = ready, 1 = just used. */
    public static float cooldownFraction(Technique t, long now) {
        long[] cd = cooldowns.get(t.id());
        if (cd == null || now >= cd[1]) return 0f;
        return (cd[1] - now) / (float) Math.max(1, cd[1] - cd[0]);
    }

    public static void clear() {
        cooldowns.clear();
        selected = 0;
    }
}
