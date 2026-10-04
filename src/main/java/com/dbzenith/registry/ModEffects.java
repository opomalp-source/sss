package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Custom status effects.
 * <ul>
 *   <li><b>Stunned</b>: cannot move, jump, attack, dash or use techniques.</li>
 *   <li><b>Ki Sealed</b>: cannot use techniques, fly, charge, transform or regenerate ki.</li>
 * </ul>
 * The rules are enforced where those actions happen; see {@link #isStunned} and {@link #isKiSealed}.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, DBZenith.MOD_ID);

    public static final RegistryObject<MobEffect> STUN = EFFECTS.register("stun", () -> new StatusEffect(MobEffectCategory.HARMFUL, 0xF2E94E)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, "6b7e3c52-5d1f-4b8e-9a51-0c0f6f2d1a01", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addAttributeModifier(Attributes.ATTACK_SPEED, "6b7e3c52-5d1f-4b8e-9a51-0c0f6f2d1a02", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final RegistryObject<MobEffect> KI_SEAL = EFFECTS.register("ki_seal", () -> new StatusEffect(MobEffectCategory.HARMFUL, 0x6A3FA0));

    private ModEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    public static boolean isStunned(LivingEntity e) {
        return STUN.isPresent() && e.hasEffect(STUN.get());
    }

    public static boolean isKiSealed(LivingEntity e) {
        return KI_SEAL.isPresent() && e.hasEffect(KI_SEAL.get());
    }

    /** MobEffect's constructor is protected. */
    private static final class StatusEffect extends MobEffect {
        StatusEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
