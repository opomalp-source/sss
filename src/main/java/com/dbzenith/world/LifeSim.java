package com.dbzenith.world;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.quest.QuestManager;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The optional life-sim layer (config {@code life_sim.*}): aging and earned titles.
 * Aging changes STR/DEX through {@code FormMath}; titles show before the player's name.
 */
public final class LifeSim {
    /** Titles in display order; each is earned by a milestone. */
    public enum Title {
        TRANSFORMED, SURVIVOR, DRAGON_SUMMONER, PATROL_COMMANDER, LEGENDARY, DIVINE, CHAMPION;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public String translationKey() {
            return "title.dbzenith." + id();
        }

        public boolean earned(PlayerData d) {
            return switch (this) {
                case TRANSFORMED -> d.hasFlag("has_transformed");
                case SURVIVOR -> d.getZenkaiCount() > 0;
                case DRAGON_SUMMONER -> d.hasFlag("summoned_dragon");
                case PATROL_COMMANDER -> QuestManager.patrolRank(d) >= QuestManager.RANK_REP.length - 1;
                case LEGENDARY -> safeLevel(d) >= 1000;
                case DIVINE -> d.hasFlag("god_ki");
                case CHAMPION -> d.hasFlag(com.dbzenith.tournament.Tournament.CHAMPION_FLAG);
            };
        }
    }

    private LifeSim() {}

    public static List<Title> earnedTitles(PlayerData d) {
        List<Title> out = new ArrayList<>();
        for (Title t : Title.values()) if (t.earned(d)) out.add(t);
        return out;
    }

    private static int safeLevel(PlayerData d) {
        try {
            return StatCalculator.level(d);
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    /** How fast a race ages (years per configured period). Androids and Majins don't age. */
    public static double agingRate(Race race) {
        return switch (race) {
            case ANDROID, MAJIN, BIO_ANDROID -> 0.0;
            case NAMEKIAN, FROST_DEMON -> 0.25;
            case VAMPIRE, CORE_PERSON -> 0.1;          // centuries pass like decades
            default -> 1.0;
        };
    }

    /**
     * Called once per second: age advances with in-game time. The body ages by race; the mind ages for everyone, three
     * times as fast while meditating. Inside the Hyperbolic Time Chamber a day is a year (on top of normal aging).
     */
    public static void tick(ServerPlayer player, PlayerData d) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!c.agingEnabled.get()) return;
        double base = 1.0 / (c.agingDaysPerYear.get() * 1200.0);
        double chamber = TimeChamber.isIn(player) ? 1.0 / 1200.0 : 0.0;
        double body = agingRate(d.getRace()) * (base + chamber);
        if (body > 0) d.setPhysicalAge(d.getPhysicalAge() + body);
        d.setMentalAge(d.getMentalAge() + base * (d.isMeditating() ? 3 : 1) + chamber);
    }

    /** Wisdom: TP gains grow with mental age past 20. */
    public static double wisdomMultiplier(PlayerData d) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!c.agingEnabled.get()) return 1.0;
        return 1.0 + Math.min(c.wisdomTpMax.get(), Math.max(0, d.getMentalAge() - 20) * c.wisdomTpPerYear.get());
    }

    /**
     * Age effect on combat attributes: children are weaker; past 60, STR and DEX decline 1% per year (max -40%).
     * Ki Power does not decline (old masters stay deadly).
     */
    public static double ageMultiplier(PlayerData d, Attribute a) {
        if (!DBZConfig.SERVER.agingEnabled.get() || a == Attribute.KI_POWER) return 1.0;
        double age = d.getPhysicalAge();
        if (age < 12) return 0.8;
        if (age <= 60) return 1.0;
        return Math.max(0.6, 1.0 - (age - 60) * 0.01);
    }
}
