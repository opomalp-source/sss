package com.dbzenith.race;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;

/** Server-side application of a race and of the character-creation choices. */
public final class CharacterCreation {
    private CharacterCreation() {}

    /** The full set of first-join choices. Validated and clamped in {@link #create}. */
    public record Choices(Race race, FightingPath path, PlayerData.BodyType body, String hairCode, int hairColor,
                          int eyeColor, int alignment, int skinTone, int heightPercent, String variant) {}

    /**
     * Sets the race: tail per race, racial techniques learned (and slotted if there is room), and a form the new
     * race can't use is dropped. Used by /dbz race and by {@link #create}.
     */
    public static void applyRace(PlayerData data, Race race) {
        RaceTraits t = Races.of(race);
        boolean changed = data.getRace() != race;
        data.setRace(race);
        if (changed) {                                          // a new race starts as its default variant, with no destiny
            data.setVariant(Variant.defaultFor(race));
            data.setDestiny("");
        }
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
        if (!form.isBase() && (!form.races().contains(race) || !form.allows(data.getVariant()))) data.setFormId(PlayerData.BASE_FORM);
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
        data.setHairCode(c.hairCode());                         // an invalid code is ignored (stays bald)
        data.setSkinTone(c.skinTone());
        data.setHeightPercent(c.heightPercent());
        data.setHairColor(c.hairColor());
        data.setEyeColor(c.eyeColor());
        data.setAlignment(c.alignment());
        chooseVariant(data, c.variant(), new java.util.Random());
        data.setCharacterCreated(true);
        data.recomputeIfStale();
        data.refill();
        return true;
    }

    /**
     * The clan or lineage picked at creation (anything else falls back to the race's default), then the destiny roll: a
     * small chance ({@code races.rareVariantChance}) that the character carries a rare variant, hidden until the first
     * milestone.
     */
    public static void chooseVariant(PlayerData data, String requested, java.util.Random random) {
        Variant chosen = Variant.byId(requested == null ? "" : requested, data.getRace());
        if (!Variant.creationChoices(data.getRace()).contains(chosen)) chosen = Variant.defaultFor(data.getRace());
        data.setVariant(chosen);
        Variant rare = Variant.rareFor(chosen);
        data.setDestiny(rare != null && random.nextDouble() < DBZConfig.SERVER.rareVariantChance.get() ? rare.id() : "");
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
