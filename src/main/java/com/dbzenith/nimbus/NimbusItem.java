package com.dbzenith.nimbus;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/**
 * The Flying Nimbus as an item (CX-40): using it binds the cloud to your soul and the item is absorbed. From then on the
 * call key brings it ("Nimbus!") and sends it away. Using another while already bound works like the call key.
 */
public class NimbusItem extends Item {
    public NimbusItem(Properties p) { super(p); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide&&player instanceof ServerPlayer sp) {
            if(player.getVehicle() instanceof NimbusEntity n) { n.togglePosture();return InteractionResultHolder.success(stack); }
            if(!NimbusEntity.eligible(player)) { player.displayClientMessage(Component.translatable("message.dbzenith.nimbus.good"),true);return InteractionResultHolder.fail(stack); }
            if(!NimbusBond.bound(player)) {
                NimbusBond.bind(sp);
                if(!player.getAbilities().instabuild) stack.shrink(1);
            } else NimbusBond.press(sp);
            player.getCooldowns().addCooldown(this,15);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("item.dbzenith.flying_nimbus.hint",Component.keybind("key.dbzenith.nimbus_call")));
    }
}
