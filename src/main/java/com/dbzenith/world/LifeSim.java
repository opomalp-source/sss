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
        TRANSFORMED, SURVIVOR, DRAGON_SUMMONER, PATROL_COMMANDER, LEGENDARY, DIVINE;

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
            case ANDROID, MAJIN -> 0.0;
            case NAMEKIAN, FROST_DEMON -> 0.25;
            default -> 1.0;
        };
    }

    /** Called once per second: physical and mental age advance with in-game time. */
    public static void tick(ServerPlayer player, PlayerData d) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!c.agingEnabled.get()) return;
        double yearsPerSecond = agingRate(d.getRace()) / (c.agingDaysPerYear.get() * 1200.0);
        if (yearsPerSecond <= 0) return;
        d.setPhysicalAge(d.getPhysicalAge() + yearsPerSecond);
        d.setMentalAge(d.getMentalAge() + yearsPerSecond);
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
