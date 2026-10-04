package com.dbzenith.stats;

import net.minecraft.nbt.CompoundTag;

/**
 * Stats computed from base attributes by {@link StatCalculator}. Immutable snapshot.
 * Computed on the server and shipped to the owning client inside the sync packet,
 * so the client never needs to repeat the math.
 *
 * @param maxBody          max "body" (DBZ health pool)
 * @param maxStamina       max stamina
 * @param maxKi            max ki
 * @param meleeDamage      flat physical attack damage
 * @param kiDamage         flat ki attack damage
 * @param defense          flat damage reduction
 * @param evasion          chance 0-1 to evade a hit
 * @param kiControl        ki cost reduction 0-1
 * @param spiritModifier   multiplier on mastery gain (1 = none)
 * @param kiTransfer       ki per second that can be given to others
 * @param attackSpeed      fractional melee attack-speed bonus
 * @param moveSpeed        fractional movement-speed bonus
 */
public record DerivedStats(
        double maxBody,
        double maxStamina,
        double maxKi,
        double meleeDamage,
        double kiDamage,
        double defense,
        double evasion,
        double kiControl,
        double spiritModifier,
        double kiTransfer,
        double attackSpeed,
        double moveSpeed) {

    public static final DerivedStats EMPTY = new DerivedStats(1, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0);

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("maxBody", maxBody);
        tag.putDouble("maxStamina", maxStamina);
        tag.putDouble("maxKi", maxKi);
        tag.putDouble("meleeDamage", meleeDamage);
        tag.putDouble("kiDamage", kiDamage);
        tag.putDouble("defense", defense);
        tag.putDouble("evasion", evasion);
        tag.putDouble("kiControl", kiControl);
        tag.putDouble("spiritModifier", spiritModifier);
        tag.putDouble("kiTransfer", kiTransfer);
        tag.putDouble("attackSpeed", attackSpeed);
        tag.putDouble("moveSpeed", moveSpeed);
        return tag;
    }

    public static DerivedStats load(CompoundTag tag) {
        return new DerivedStats(
                tag.getDouble("maxBody"),
                tag.getDouble("maxStamina"),
                tag.getDouble("maxKi"),
                tag.getDouble("meleeDamage"),
                tag.getDouble("kiDamage"),
                tag.getDouble("defense"),
                tag.getDouble("evasion"),
                tag.getDouble("kiControl"),
                tag.getDouble("spiritModifier"),
                tag.getDouble("kiTransfer"),
                tag.getDouble("attackSpeed"),
                tag.getDouble("moveSpeed"));
    }
}
