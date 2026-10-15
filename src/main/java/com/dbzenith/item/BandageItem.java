package com.dbzenith.item;

import com.dbzenith.combat.Limbs;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A bandage (the knocked-out rework): binds a fresh wound so it stops bleeding, and tends the most hurt limb (a third
 * of its health back). Use it on yourself, or on someone else (the downed too: it doesn't get them up, but it stops
 * them bleeding where they lie).
 */
public class BandageItem extends Item {
    public BandageItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!apply(player, player)) {
            player.displayClientMessage(Component.translatable("message.dbzenith.bandage_nothing"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!apply(player, target)) return InteractionResult.PASS;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("message.dbzenith.bandaged", target.getDisplayName()), true);
        return InteractionResult.CONSUME;
    }

    /** Binds {@code target}'s wounds; false if there was nothing to bind. */
    static boolean apply(Player by, LivingEntity target) {
        if (!Limbs.eligible(target)) return false;
        boolean bled = Limbs.stopBleeding(target);
        Limbs.Limb worst = Limbs.worst(target);
        if (!bled && worst == null) return false;
        if (worst != null) Limbs.tend(target, worst, 34);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1f, 1.2f);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.dbzenith.bandage.hint"));
    }
}
