package com.dbzenith.skill;

/**
 * A ki technique definition. Pure data; firing logic lives in {@link TechniqueHandler}.
 *
 * @param id             stable id (lang key {@code technique.dbzenith.<id>})
 * @param kiCost         base ki cost before ki control / release scaling
 * @param damageMult     multiplier on {@code DamageCalculator.kiOutgoing}
 * @param speed          blocks per tick
 * @param size           projectile diameter in blocks
 * @param cooldownTicks  ticks before it can be used again
 * @param count          projectiles per use (volleys)
 * @param spreadDegrees  random cone for multi-projectile techniques
 * @param pierce         extra entities a projectile passes through
 * @param homing         steers toward the target it was aimed at
 * @param explosionPower vanilla explosion power on impact (0 = none)
 * @param color          0xRRGGBB tint
 * @param lifeTicks      max flight time
 * @param style          visual style
 */
public record Technique(
        String id,
        double kiCost,
        double damageMult,
        float speed,
        float size,
        int cooldownTicks,
        int count,
        float spreadDegrees,
        int pierce,
        boolean homing,
        float explosionPower,
        int color,
        int lifeTicks,
        Style style) {

    public enum Style { BALL, DISK }

    public String translationKey() {
        return "technique.dbzenith." + id;
    }
}
