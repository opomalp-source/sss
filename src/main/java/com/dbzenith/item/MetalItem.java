package com.dbzenith.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** An ingot, raw ore or alloy blend of one of the metals (12e); its tooltip tells the tier and what plating with it gives. */
public class MetalItem extends Item {
    private final Metal metal;

    public MetalItem(Metal metal, Properties properties) {
        super(properties);
        this.metal = metal;
    }

    public Metal metal() {
        return metal;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dbzenith.metal_tier", metal.tier()).withStyle(ChatFormatting.GRAY));
        if (metal.ingot() == this) {
            tooltip.add(Component.translatable("item.dbzenith.metal_plating", String.format("%.1f", metal.platingReduction() * 100))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
