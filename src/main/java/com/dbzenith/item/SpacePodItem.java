package com.dbzenith.item;

import com.dbzenith.world.SpacePodEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A personal space pod. Used on the ground it is set down as a pod you can climb into (it launches and lands);
 * used in the air it opens the destination screen for an instant trip. Travel is validated on the server.
 */
public class SpacePodItem extends Item {
    public SpacePodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!level.isClientSide) {
            Vec3 at = Vec3.atBottomCenterOf(ctx.getClickedPos().relative(ctx.getClickedFace()));
            SpacePodEntity pod = SpacePodEntity.create(level, at);
            if (ctx.getPlayer() != null) pod.setYRot(ctx.getPlayer().getYRot() + 180f);
            if (!level.noCollision(pod, pod.getBoundingBox())) return InteractionResult.FAIL;
            level.addFreshEntity(pod);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) com.dbzenith.client.ClientHooks.openPlanetScreen(); // client-only class; never runs on a server
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
