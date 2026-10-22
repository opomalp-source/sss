package com.dbzenith.nemesis;

import com.dbzenith.npc.ai.AiRandom;
import com.dbzenith.npc.ai.Personality;

import java.util.List;
import java.util.Map;

/**
 * Names for promoted fighters, original but in the series' own naming habits: Saiyans after vegetables, the Frieza
 * Force after fruit, Saibamen after sprouts, Namekians after instruments, Red Ribbon Androids by unit number, the
 * damned and the ogres after their kind. Epithets come from temperament and traits ("the Proud", "the Unforgiving")
 * and from what a character has done ("Saiyan-Breaker"). Canon characters (Frieza, Broly, the tournament roster)
 * keep their own names.
 */
public final class Names {
    private Names() {}

    private static final Map<String, List<String>> BY_TYPE = Map.of(
            "saiyan_warrior", List.of("Kabura", "Gobou", "Rapinn", "Okran", "Leekan", "Parsnov", "Rutabar", "Endivo", "Chardon", "Fennek",
                    "Jicamo", "Radichi", "Arugo", "Kolrab", "Bokchoi", "Sorrel", "Celeric", "Collard", "Shiso", "Mizuna", "Komatsu", "Taroh"),
            "ki_soldier", List.of("Mangor", "Lychee", "Pomegra", "Kumquo", "Durian", "Papayo", "Guavan", "Quincel", "Plumbo", "Rambutan",
                    "Persimo", "Medlar", "Yuzurr", "Sapodil", "Loquat", "Tamaril"),
            "sproutling", List.of("Mungo", "Alfal", "Cressen", "Bean", "Soyaro", "Pea-Shoot", "Radisprout", "Clovern"),
            "namekian_warrior", List.of("Oboen", "Tymp", "Cymba", "Lutar", "Banjol", "Kazu", "Sitarr", "Harpen", "Celest", "Marimba"),
            "android_unit", List.of("Unit R-41", "Unit R-46", "Unit R-52", "Unit R-58", "Unit R-63", "Unit R-67", "Unit R-74", "Unit R-79"),
            "damned_warrior", List.of("Morrow", "Gravell", "Sorrowk", "Vexan", "Dirgeon", "Ashkar", "Wrathe", "Grimald"),
            "ogre_guard", List.of("Akabo", "Aokuro", "Kinjo", "Tetsuo", "Gongo", "Zakuro"),
            // the people of Ginger Town, after spices
            "resident", List.of("Nutmeg", "Clove", "Cinna", "Saffra", "Paprik", "Turmen", "Cardam", "Anisse", "Sumako", "Macey", "Tarrag",
                    "Chervy", "Cumino", "Sansho", "Shichi", "Fenugre", "Galang", "Ajowan", "Wasab", "Mustar", "Pimento", "Sesamy", "Corian", "Lovage",
                    "Basilo", "Thymon", "Rosmar", "Oregan", "Dillon", "Sagey", "Parsli", "Chivo", "Junipa", "Vanilo", "Licoris", "Caraway", "Borage",
                    "Hyssop", "Marjor", "Annatto", "Nigella", "Zaatar", "Epazot", "Kokum", "Amchur", "Kalonji", "Perilla", "Mitsuba", "Tamarin", "Ginsen"));

    /** A name not in use (falls back to a numbered one). */
    public static String pick(String type, long seed, java.util.Collection<NamedFighter> taken) {
        List<String> pool = BY_TYPE.getOrDefault(type, List.of("Kurogane", "Hayate", "Raiko", "Tobei"));
        int start = AiRandom.pick(seed, 1, 3, pool.size());
        for (int i = 0; i < pool.size(); i++) {
            String n = pool.get((start + i) % pool.size());
            if (taken.stream().noneMatch(c -> c.name.equals(n))) return n;
        }
        return pool.get(start) + " " + (2 + Math.floorMod(seed, 8));
    }

    /** The epithet a character earns from its nature and deeds. */
    public static String epithet(NamedFighter c) {
        if (c.canon) return "";
        for (Bond b : c.players.values()) {
            if (b.wins >= 3 && b.wins > b.losses * 2) return "the " + (c.race.equals("saiyan") ? "Saiyan-Breaker" : "Undefeated");
        }
        if (c.survivedDeaths >= 2) return "the Unkillable";
        if (c.has(Trait.VENGEFUL)) return "the Unforgiving";
        if (c.has(Trait.AMBITIOUS)) return "the Rising";
        if (c.has(Trait.INTELLIGENT)) return "the Clever";
        if (c.has(Trait.CALM)) return "the Still";
        if (c.has(Trait.COMPETITIVE)) return "the Challenger";
        return switch (c.personality) {
            case ARROGANT -> "the Proud";
            case RECKLESS -> "the Wild";
            case COWARDLY -> "the Slippery";
            case HONORABLE -> "the Just";
            case TACTICAL -> "the Cunning";
            case PATIENT -> "the Patient";
            case AGGRESSIVE -> "the Fierce";
            case DEFENSIVE -> "the Wall";
            default -> "";
        };
    }

    /** Traits a character is born with (one or two, from its seed, leaning on its temperament). */
    public static java.util.Set<Trait> bornTraits(long seed, Personality p) {
        java.util.Set<Trait> out = java.util.EnumSet.noneOf(Trait.class);
        Trait[] all = Trait.values();
        out.add(all[AiRandom.pick(seed, 2, 5, all.length)]);
        if (AiRandom.chance(seed, 3, 5, 0.45)) out.add(all[AiRandom.pick(seed, 4, 5, all.length)]);
        if (p == Personality.HONORABLE) out.remove(Trait.VENGEFUL);
        if (p == Personality.RECKLESS) out.remove(Trait.CALM);
        if (out.contains(Trait.FRIENDLY) && out.contains(Trait.VENGEFUL)) out.remove(Trait.FRIENDLY);
        return out;
    }
}
