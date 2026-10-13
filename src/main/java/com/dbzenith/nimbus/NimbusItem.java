package com.dbzenith.nimbus;

import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public class NimbusItem extends Item {
    public NimbusItem(Properties p) { super(p); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide) {
            if(player.getVehicle() instanceof NimbusEntity n) { n.togglePosture();return InteractionResultHolder.success(stack); }
            if(!NimbusEntity.eligible(player)) { player.displayClientMessage(Component.translatable("message.dbzenith.nimbus.good"),true);return InteractionResultHolder.fail(stack); }
            if(player.isPassenger()) return InteractionResultHolder.fail(stack);
            Vec3 aim=player.getLookAngle().multiply(1,0,1).normalize();
            NimbusEntity cloud=new NimbusEntity(NimbusContent.TYPE,level);
            Vec3 at=player.position().add(aim.scale(1.8)).add(0,.3,0);
            cloud.moveTo(at.x,at.y,at.z,player.getYRot(),0);
            if(!level.noCollision(cloud,cloud.getBoundingBox().expandTowards(0,1.9,0))) {
                player.displayClientMessage(Component.translatable("message.dbzenith.nimbus.space"),true);return InteractionResultHolder.fail(stack);
            }
            // A reusable summon; remove only this owner's empty previous cloud nearby to avoid duplicates.
            for(NimbusEntity old:level.getEntitiesOfClass(NimbusEntity.class,player.getBoundingBox().inflate(32))) {
                if(player.getUUID().toString().equals(old.getPersistentData().getString("NimbusOwner"))&&!old.isVehicle()) old.discard();
            }
            cloud.getPersistentData().putString("NimbusOwner",player.getUUID().toString());
            level.addFreshEntity(cloud);cloud.interact(player,hand);player.getCooldowns().addCooldown(this,15);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("item.dbzenith.flying_nimbus.hint"));
    }
}
