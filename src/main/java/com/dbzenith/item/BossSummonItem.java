package com.dbzenith.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Supplier;

/** Used on the ground, calls a boss to fight. The boss's level matches the strongest nearby fighter. */
public class BossSummonItem extends Item {
    private final Supplier<? extends EntityType<? extends Mob>> boss;

    public BossSummonItem(Supplier<? extends EntityType<? extends Mob>> boss, Properties properties) {
        super(properties);
        this.boss = boss;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel() instanceof ServerLevel level) {
            BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
            Mob mob = boss.get().spawn(level, ctx.getItemInHand(), ctx.getPlayer(), at, MobSpawnType.TRIGGERED, true, false);
            if (mob != null) {
                if (ctx.getPlayer() != null && !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
                level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.dbzenith.boss_arrives",
                        mob.getDisplayName()), false);
            }
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }
}
