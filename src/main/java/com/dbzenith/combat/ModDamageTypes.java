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

    private ModDamageTypes() {}

    /** A ki blast hit. The hurt amount passed with this source is raw DBZ damage, converted in CombatEvents. */
    public static DamageSource kiBlast(Level level, Entity projectile, Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KI_BLAST), projectile, owner);
    }

    /** A thrown body hitting the ground or a wall. The amount is raw DBZ damage, like a ki blast. */
    public static DamageSource thrown(Level level, Entity thrower) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(THROW), thrower, thrower);
    }
}
