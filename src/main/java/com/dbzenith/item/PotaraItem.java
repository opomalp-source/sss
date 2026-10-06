package com.dbzenith.item;

import com.dbzenith.fusion.FusionDance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Potara earrings (12c): a pair of gold earrings with green beads, from the Kais. Use them on another player to offer
 * one; if they put it on, the two are pulled together and fuse (see {@link FusionDance}). The pair is spent then.
 */
public class PotaraItem extends Item {
    public PotaraItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer from && target instanceof ServerPlayer to) FusionDance.request(from, to, FusionDance.POTARA);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dbzenith.potara_earrings.tip").withStyle(ChatFormatting.GRAY));
    }
}
