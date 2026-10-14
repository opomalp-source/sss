package com.dbzenith.item;

import com.dbzenith.combat.Limbs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A prosthetic arm or leg (CX-54): use it to fit it where you lost that limb (the right side first). It works as the
 * limb did: it can be hurt and broken, and a cutting blow can take it off again.
 */
public class ProstheticItem extends Item {
    private final boolean arm;

    public ProstheticItem(Properties p, boolean arm) {
        super(p);
        this.arm = arm;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!Limbs.fit(player, arm)) {
            player.displayClientMessage(Component.translatable(arm ? "message.dbzenith.prosthetic_no_arm" : "message.dbzenith.prosthetic_no_leg"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.dbzenith.prosthetic.hint"));
    }
}
