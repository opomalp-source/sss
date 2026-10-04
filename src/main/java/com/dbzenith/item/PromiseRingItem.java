package com.dbzenith.item;

import com.dbzenith.world.Family;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Use on another player to ask them to be your partner (see {@link Family}). */
public class PromiseRingItem extends Item {
    public PromiseRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer from && target instanceof ServerPlayer to) Family.propose(from, to);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
