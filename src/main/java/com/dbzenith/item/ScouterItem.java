package com.dbzenith.item;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Scouter (helmet slot): the HUD shows the power level of what you look at. Reading a power level above
 * {@code gear.scouterLimit} overloads it: it shatters.
 */
public class ScouterItem extends ArmorItem {
    public static final double RANGE = 64;

    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return 200; }
        @Override public int getDefenseForType(Type type) { return 1; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.GLASS_PANE); }
        @Override public String getName() { return DBZenith.MOD_ID + ":scouter"; }
        @Override public float getToughness() { return 0; }
        @Override public float getKnockbackResistance() { return 0; }
    };

    public ScouterItem(Properties properties) {
        super(MATERIAL, Type.HELMET, properties);
    }

    public static boolean wears(Player p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ScouterItem;
    }

    /** Power level of any living thing: players from their stats, creatures from health and armor. */
    public static long powerOf(LivingEntity e) {
        if (e instanceof Player p) {
            return ModCapabilities.get(p).map(d -> d.hasFlag("god_ki") ? 0L : StatCalculator.battlePower(d)).orElse(0L); // god ki is invisible to machines
        }
        return mobPower(e);
    }

    public static long mobPower(LivingEntity e) {
        return Math.round(e.getMaxHealth() * 10 + e.getAttributeValue(Attributes.ARMOR) * 25);
    }

    public static double range() {
        return com.dbzenith.config.DBZConfig.SERVER_SPEC.isLoaded() ? com.dbzenith.config.DBZConfig.SERVER.scouterRange.get() : RANGE;
    }

    /** The living entity under the crosshair within {@link #RANGE}, or null. */
    public static LivingEntity target(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range()));
        AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range())).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        Entity e = hit == null ? null : hit.getEntity();
        return e instanceof LivingEntity le ? le : null;
    }

    /** Server, once per second for wearers: shatter on readings above the limit. Returns true if it broke. */
    public static boolean checkOverload(ServerPlayer player) {
        if (!wears(player)) return false;
        LivingEntity t = target(player);
        if (t == null || powerOf(t) <= DBZConfig.SERVER.scouterLimit.get()) return false;
        ItemStack scouter = player.getItemBySlot(EquipmentSlot.HEAD);
        scouter.shrink(1);
        player.serverLevel().sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getEyeY(), player.getZ(), 1, 0, 0, 0, 0);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("message.dbzenith.scouter_overload"), true);
        return true;
    }
}
