package com.dbzenith.dragonball;

import java.util.Locale;

/**
 * The three sets of seven (12d). Each has its own blocks, its own tracking ({@link DragonBallData}), its own way of
 * scattering, and its own dragon with its own wishes.
 * <ul>
 * <li><b>Earth's Dragon Balls</b>: orange, scattered around spawn; the Eternal Dragon, green.</li>
 * <li><b>Black Star Dragon Balls</b>: deep red with black stars, scattered across the other planets; the red Black Star
 * Dragon. A wish on them is cursed: gather the seven again before the curse runs out, or meteors rain on Earth.</li>
 * <li><b>Super Dragon Balls</b>: huge, golden, scattered far out in the world; the gigantic golden dragon, whose wishes
 * no other dragon can grant.</li>
 * </ul>
 */
public enum BallSet {
    EARTH("dragon_ball", "dbzenith_dragon_balls"),
    BLACK_STAR("black_star_ball", "dbzenith_black_star_balls"),
    SUPER("super_dragon_ball", "dbzenith_super_dragon_balls");

    private final String prefix;
    private final String saveName;

    BallSet(String prefix, String saveName) {
        this.prefix = prefix;
        this.saveName = saveName;
    }

    /** Block id of a ball: {@code prefix_star}. */
    public String prefix() {
        return prefix;
    }

    String saveName() {
        return saveName;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Radar blips number balls 1-7, 8-14 and 15-21 by set. */
    public int radarOffset() {
        return ordinal() * DragonBalls.COUNT;
    }

    public static BallSet byOrdinal(int i) {
        BallSet[] all = values();
        return i >= 0 && i < all.length ? all[i] : EARTH;
    }
}
