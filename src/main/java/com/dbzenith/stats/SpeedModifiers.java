package com.dbzenith.stats;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Applies DEX-derived attack/move speed bonuses as vanilla attribute modifiers (multiply base). */
public final class SpeedModifiers {
    private static final UUID MOVE_ID = UUID.fromString("6b4d7c0e-2f3a-4d51-9b1e-0d1b2c5e7a11");
    private static final UUID ATTACK_ID = UUID.fromString("9a2e41f3-7c6b-4e0d-8a5f-3c2d1b0e9f22");

    private SpeedModifiers() {}

    public static void apply(Player player, DerivedStats stats) {
        set(player, Attributes.MOVEMENT_SPEED, MOVE_ID, "dbzenith dexterity move", stats.moveSpeed());
        set(player, Attributes.ATTACK_SPEED, ATTACK_ID, "dbzenith dexterity attack", stats.attackSpeed());
    }

    private static void set(Player player, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier current = inst.getModifier(id);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-6) return;
        if (current != null) inst.removeModifier(id);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }
}
