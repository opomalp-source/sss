package com.dbzenith.race;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import net.minecraft.util.Mth;

/** Server-side application of a race and of the character-creation choices. */
public final class CharacterCreation {
    private CharacterCreation() {}

    /** The full set of first-join choices. Validated and clamped in {@link #create}. */
    public record Choices(Race race, FightingPath path, PlayerData.BodyType body, int hairStyle, int hairColor,
                          int eyeColor, int alignment) {}

    /**
     * Sets the race: tail per race, racial techniques learned (and slotted if there is room), and a form the new
     * race can't use is dropped. Used by /dbz race and by {@link #create}.
     */
    public static void applyRace(PlayerData data, Race race) {
        RaceTraits t = Races.of(race);
        data.setRace(race);
        data.setTail(t.tail());
        for (String id : java.util.List.copyOf(data.learnedView())) { // drop the old race's racial techniques
            com.dbzenith.skill.Technique known = com.dbzenith.skill.Techniques.byId(id);
            if (known != null && !known.races().contains(race)) data.forget(id);
        }
        for (String id : t.racialTechniques()) {
            data.learn(id);
            if (!data.deckView().contains(id) && data.deckView().size() < com.dbzenith.skill.TechniqueLibrary.deckSlots(data)) {
                java.util.List<String> deck = new java.util.ArrayList<>(data.deckView());
                deck.add(id);
                data.setDeck(deck);
            }
        }
        Form form = Forms.byId(data.getFormId());
        if (!form.isBase() && !form.races().contains(race)) data.setFormId(PlayerData.BASE_FORM);
        data.recomputeIfStale();
    }

    /** First-join creation. Returns false if the character was already created. */
    public static boolean create(PlayerData data, Choices c) {
        if (data.isCharacterCreated()) return false;
        applyRace(data, c.race());
        RaceTraits t = Races.of(c.race());
        int cap = DBZConfig.SERVER.attributeHardCap.get();
        for (Attribute a : Attribute.values()) {
            int bonus = t.startBonus(a) + bodyBonus(c.body(), a);
            if (bonus != 0) data.setAttribute(a, Math.min(cap, data.getAttribute(a) + bonus));
        }
        data.setPath(c.path());
        data.setBodyType(c.body());
        data.setHairStyle(Mth.clamp(c.hairStyle(), 0, Form.HairStyle.values().length - 1));
        data.setHairColor(c.hairColor());
        data.setEyeColor(c.eyeColor());
        data.setAlignment(c.alignment());
        data.setCharacterCreated(true);
        data.recomputeIfStale();
        data.refill();
        return true;
    }

    /** Small stat flavor for body type. */
    public static int bodyBonus(PlayerData.BodyType body, Attribute a) {
        return switch (body) {
            case SLIM -> a == Attribute.DEXTERITY ? 3 : 0;
            case NORMAL -> a == Attribute.STRENGTH || a == Attribute.DEXTERITY || a == Attribute.CONSTITUTION ? 1 : 0;
            case BULKY -> a == Attribute.CONSTITUTION ? 3 : 0;
        };
    }
}
