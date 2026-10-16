package com.dbzenith.item;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
public final class SpaceSuitItem extends ArmorItem {
    public SpaceSuitItem(Type type){super(ArmorMaterials.IRON,type,new Item.Properties().stacksTo(1));}
    @Override public String getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,String type){return "dbzenith:textures/models/armor/space_suit_layer_"+(slot==EquipmentSlot.LEGS?2:1)+".png";}
    @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,level,lines,flag);
        lines.add(net.minecraft.network.chat.Component.translatable("item.dbzenith.space_suit.seal").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    public static boolean sealed(LivingEntity e){for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})if(!(e.getItemBySlot(slot).getItem() instanceof SpaceSuitItem))return false;return true;}
    @Override public void onArmorTick(ItemStack stack,Level level,Player player){if(!level.isClientSide&&getType()==Type.HELMET&&sealed(player))player.setAirSupply(player.getMaxAirSupply());}
}
