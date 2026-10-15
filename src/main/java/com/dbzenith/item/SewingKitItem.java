package com.dbzenith.item;

import com.dbzenith.combat.Clothes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A sewing kit (the knocked-out rework): clothes torn in battle stay torn until mended, and this mends them. Use it to
 * stitch up your own clothes and patch the gi you wear (each piece half way back; an anvil and the gi's material do the
 * rest). Six uses.
 */
public class SewingKitItem extends Item {
    public SewingKitItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!Clothes.mend(player, 0.5f)) {
            player.displayClientMessage(Component.translatable("message.dbzenith.sewing_nothing"), true);
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.dbzenith.sewn"), true);
        if (!player.getAbilities().instabuild) stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.dbzenith.sewing_kit.hint"));
    }
}
