package com.dbzenith.client.render;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.race.RaceTraits;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * How each NPC looks beyond its painted skin (CX-12): its 3D hair, race parts, tail and the things it wears, so NPCs
 * are built from the same parts as players. Keyed by the skin name its renderer uses.
 */
public final class NpcLooks {
    /** Pieces an NPC can wear (see {@link NpcExtrasModel}). */
    public enum Extra { SHELL, CAP, HAT, SCOUTER, EARRINGS, DOME, HALO, PADS, FROST_PADS, NAMEK_PADS, CAPE }

    /**
     * @param hair        a hair preset, or null for none
     * @param feature     race parts on the head (antennae, horns...) and whether pointed ears come with them
     * @param tailColor   a tail's colour, or -1 for none; {@code wrapped} winds it round the waist
     */
    public record Look(HairCode.Preset hair, int hairColor, RaceTraits.Feature feature, boolean ears, int featureColor,
                       int tailColor, boolean wrapped, Set<Extra> extras) {
        public String hairCode() {
            return hair == null ? "" : hair.code();
        }
    }

    private static Look look(HairCode.Preset hair, int hairColor, RaceTraits.Feature feature, boolean ears, int featureColor,
                             int tailColor, boolean wrapped, Extra... extras) {
        Set<Extra> set = EnumSet.noneOf(Extra.class);
        set.addAll(java.util.List.of(extras));
        return new Look(hair, hairColor, feature, ears, featureColor, tailColor, wrapped, set);
    }

    private static final Look PLAIN = look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false);

    private static final Map<String, Look> LOOKS = Map.ofEntries(
            Map.entry("martial_arts_master", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false, Extra.SHELL)),
            Map.entry("patrol_officer", look(HairCode.Preset.BUZZ, 0x3A2414, RaceTraits.Feature.NONE, false, 0, -1, false, Extra.CAP)),
            Map.entry("ki_soldier", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false, Extra.SCOUTER, Extra.FROST_PADS)),
            Map.entry("android_unit", look(HairCode.Preset.SLICK, 0x141418, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("sproutling", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false, Extra.DOME)),
            Map.entry("tyrant_lord", look(null, 0, RaceTraits.Feature.NONE, false, 0, 0xF0EEF4, false)),
            Map.entry("rampage_brute", look(null, 0, RaceTraits.Feature.NONE, false, 0, 0x6B3E1E, true, Extra.PADS)),
            Map.entry("namekian_warrior", look(null, 0, RaceTraits.Feature.ANTENNAE, true, 0x62B444, -1, false, Extra.NAMEK_PADS, Extra.CAPE)),
            Map.entry("enma", look(HairCode.Preset.WILD, 0x181216, RaceTraits.Feature.NONE, false, 0, -1, false, Extra.HAT)),
            Map.entry("ogre_clerk_red", look(HairCode.Preset.PUFF, 0x141010, RaceTraits.Feature.DEMON_HORNS, false, 0xF0E0C0, -1, false)),
            Map.entry("ogre_clerk_blue", look(HairCode.Preset.PUFF, 0x141010, RaceTraits.Feature.DEMON_HORNS, false, 0xF0E0C0, -1, false)),
            Map.entry("ogre_guard", look(HairCode.Preset.WILD, 0x141010, RaceTraits.Feature.DEMON_HORNS, false, 0xF0E0C0, -1, false)),
            Map.entry("north_kai", look(null, 0, RaceTraits.Feature.ANTENNAE, false, 0x2A2A3A, -1, false)),
            Map.entry("grand_kai", look(HairCode.Preset.SWEPT, 0xF4F2F0, RaceTraits.Feature.NONE, true, 0xC8A6E0, -1, false, Extra.EARRINGS)),
            Map.entry("beerus", look(null, 0, RaceTraits.Feature.EARS, true, 0xB49CCC, 0xB49CCC, false, Extra.EARRINGS)),
            Map.entry("whis", look(HairCode.Preset.MOHAWK, 0xF2F4F8, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("tournament_announcer", look(HairCode.Preset.SLICK, 0xF0D060, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("mr_satan", look(HairCode.Preset.PUFF, 0x141210, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("spopovich", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("pintar", look(HairCode.Preset.LONG, 0x1A1414, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("jewel", look(HairCode.Preset.LONG, 0xF0D890, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("nam", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("ranfan", look(HairCode.Preset.PONYTAIL, 0x1A1418, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("yamu", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("goku", look(HairCode.Preset.SPIKY, 0x141418, RaceTraits.Feature.NONE, false, 0, -1, false)),            // the style masters (CX-20)
            Map.entry("vegeta", look(HairCode.Preset.PRINCE, 0x141418, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("master_roshi", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false, Extra.SHELL)),
            Map.entry("krillin", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("piccolo", look(null, 0, RaceTraits.Feature.ANTENNAE, true, 0x5AA040, -1, false, Extra.NAMEK_PADS, Extra.CAPE)),
            Map.entry("tien", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("gohan", look(HairCode.Preset.TEEN, 0x141418, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("future_trunks", look(HairCode.Preset.CURTAINS, 0xB8A8E0, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("frieza", look(null, 0, RaceTraits.Feature.NONE, false, 0, 0xF0EEF4, false)),
            Map.entry("cell", look(null, 0, RaceTraits.Feature.NONE, false, 0, 0x6AAA40, false)),
            Map.entry("android_17", look(HairCode.Preset.CURTAINS, 0x141418, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("hit", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("jiren", look(null, 0, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("broly", look(HairCode.Preset.WILD, 0x141418, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("yamcha", look(HairCode.Preset.SWEPT, 0x141418, RaceTraits.Feature.NONE, false, 0, -1, false)),
            Map.entry("majin_buu", look(null, 0, RaceTraits.Feature.TENTACLE, false, 0xF0A0C8, -1, false, Extra.CAPE)),
            Map.entry("training_monkey", look(null, 0, RaceTraits.Feature.NONE, false, 0, 0x8A5A30, false)),
            Map.entry("damned_warrior", look(HairCode.Preset.WILD, 0x2A2430, RaceTraits.Feature.NONE, false, 0, -1, false)));

    private NpcLooks() {}

    /** How long an NPC's race parts are (1 classic): Beerus's great cat ears. */
    public static float featureLength(String skin) {
        return "beerus".equals(skin) ? 2.1f : 1f;
    }

    public static Look of(String skin) {
        return LOOKS.getOrDefault(skin, PLAIN);
    }
}
