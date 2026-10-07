package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Data-driven damage types (JSON under data/dbzenith/damage_type). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> KI_BLAST =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DBZenith.MOD_ID, "ki_blast"));

    public static final ResourceKey<DamageType> THROW =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DBZenith.MOD_ID, "throw"));

    public static final ResourceKey<DamageType> ABSORBED =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DBZenith.MOD_ID, "absorbed"));

    /** A blow from the combat engine (CX-19): the amount is finished DBZ damage; knockback is the engine's own. */
    public static final ResourceKey<DamageType> STRIKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DBZenith.MOD_ID, "strike"));

    public static final ResourceKey<DamageType> HAKAI =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DBZenith.MOD_ID, "hakai"));

    private ModDamageTypes() {}

    /** A ki blast hit. The hurt amount passed with this source is raw DBZ damage, converted in CombatEvents. */
    public static DamageSource kiBlast(Level level, Entity projectile, Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KI_BLAST), projectile, owner);
    }

    /** A thrown body hitting the ground or a wall. The amount is raw DBZ damage, like a ki blast. */
    public static DamageSource thrown(Level level, Entity thrower) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(THROW), thrower, thrower);
    }

    /** Absorbed by a Majin: certain death (bypasses invulnerability, armor and resistance via damage-type tags). */
    public static DamageSource absorbed(Level level, Entity majin) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ABSORBED), majin, majin);
    }

    /** Erased by a Hakai: certain death, like absorption. */
    public static DamageSource hakai(Level level, Entity destroyer) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(HAKAI), destroyer, destroyer);
    }

    public static DamageSource strike(Level level, Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(STRIKE), attacker, attacker);
    }
}
