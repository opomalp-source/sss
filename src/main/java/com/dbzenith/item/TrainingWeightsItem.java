package com.dbzenith.item;

import com.dbzenith.DBZenith;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

/** Weighted training vest (chest slot): slows you down, multiplies training gains. No protection. */
public class TrainingWeightsItem extends ArmorItem {
    private static final UUID SLOW_ID = UUID.fromString("7e3a1d22-9c4b-4f60-8d2e-5b1f0a6c3e77");

    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return 600; }
        @Override public int getDefenseForType(Type type) { return 0; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.IRON_INGOT); }
        @Override public String getName() { return DBZenith.MOD_ID + ":weights"; }
        @Override public float getToughness() { return 0; }
        @Override public float getKnockbackResistance() { return 0.1f; }
    };

    private final double trainingFactor;
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public TrainingWeightsItem(double trainingFactor, double slow, Properties properties) {
        super(MATERIAL, Type.CHESTPLATE, properties);
        this.trainingFactor = trainingFactor;
        this.modifiers = ImmutableMultimap.of(Attributes.MOVEMENT_SPEED,
                new AttributeModifier(SLOW_ID, "dbzenith training weights", -slow, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    public double trainingFactor() {
        return trainingFactor;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.CHEST ? modifiers : ImmutableMultimap.of();
    }
}
