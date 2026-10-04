package com.dbzenith.item;

import com.dbzenith.data.ModCapabilities;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Eaten instantly-ish; fully restores health, body, ki and stamina, and fills hunger. */
public class SenzuBeanItem extends Item {
    public static final FoodProperties FOOD = new FoodProperties.Builder()
            .nutrition(20).saturationMod(1.0f).alwaysEat().fast().build();

    public SenzuBeanItem(Properties properties) {
        super(properties.food(FOOD));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.setHealth(entity.getMaxHealth());
            if (entity instanceof Player player) {
                ModCapabilities.get(player).ifPresent(data -> data.refill());
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
