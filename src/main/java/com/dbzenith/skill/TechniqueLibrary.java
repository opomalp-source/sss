package com.dbzenith.skill;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Learning techniques and editing the deck. Server-authoritative; the client only requests. */
public final class TechniqueLibrary {
    private TechniqueLibrary() {}

    /** Number of deck slots: base + 1 per {@code deckLevelsPerExtraSlot} levels, capped. */
    public static int deckSlots(PlayerData data) {
        DBZConfig.Server c = DBZConfig.SERVER;
        int slots = c.deckBaseSlots.get() + StatCalculator.level(data) / c.deckLevelsPerExtraSlot.get();
        return Math.min(c.deckMaxSlots.get(), slots);
    }

    /** Why {@code t} can't be learned with TP right now, or null if it can. */
    public static Component learnProblem(PlayerData data, Technique t) {
        if (data.knows(t.id())) return Component.translatable("technique.dbzenith.problem.known");
        if (!t.races().contains(data.getRace())) return Component.translatable("technique.dbzenith.problem.race");
        if (StatCalculator.level(data) < t.unlockLevel()) return Component.translatable("technique.dbzenith.problem.level", t.unlockLevel());
        if (data.getTrainingPoints() < t.learnCost()) return Component.translatable("technique.dbzenith.problem.tp", t.learnCost());
        return null;
    }

    /** Spend TP to learn. Returns true on success. */
    public static boolean learnWithTp(PlayerData data, Technique t) {
        if (t == null || learnProblem(data, t) != null) return false;
        data.setTrainingPoints(data.getTrainingPoints() - t.learnCost());
        data.learn(t.id());
        autoEquip(data, t.id());
        return true;
    }

    /** Learn for free (scrolls, teachers, quests); only race is checked. */
    public static boolean learnFree(PlayerData data, Technique t) {
        if (t == null || data.knows(t.id()) || !t.races().contains(data.getRace())) return false;
        data.learn(t.id());
        autoEquip(data, t.id());
        return true;
    }

    private static void autoEquip(PlayerData data, String id) {
        if (data.deckView().size() < deckSlots(data) && !data.deckView().contains(id)) {
            List<String> deck = new ArrayList<>(data.deckView());
            deck.add(id);
            data.setDeck(deck);
        }
    }

    /**
     * Sets the deck from a client request: unknown or unlearned ids are dropped, duplicates removed,
     * and the list is cut to the slot count. Returns the deck actually stored.
     */
    public static List<String> setDeck(PlayerData data, List<String> requested) {
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String id : requested) {
            if (Techniques.byId(id) != null && data.knows(id)) clean.add(id);
        }
        List<String> deck = new ArrayList<>(clean).subList(0, Math.min(clean.size(), deckSlots(data)));
        data.setDeck(new ArrayList<>(deck));
        return data.deckView();
    }
}
