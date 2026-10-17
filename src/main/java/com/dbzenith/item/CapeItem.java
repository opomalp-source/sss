package com.dbzenith.item;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerDataEvents;
import com.dbzenith.registry.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import java.util.List;
import java.util.function.Supplier;

/** A separate cosmetic back slot, retained with character data through death like other appearance choices. */
public final class CapeItem extends Item {
    public CapeItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) equip(server, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    public static boolean equip(ServerPlayer player, InteractionHand hand) {
        if (!player.isAlive() || player.isSpectator()) return false;
        var data = ModCapabilities.getOrThrow(player);
        var held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof CapeItem)) return false;
        ItemStack previous = data.capeAccessory();
        data.setCapeAccessory(held);
        held.shrink(1);
        if (held.isEmpty()) player.setItemInHand(hand, previous);
        else returnItem(player, previous);
        changed(player);
        return true;
    }

    public static boolean remove(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return false;
        var data = ModCapabilities.getOrThrow(player);
        ItemStack old = data.capeAccessory();
        if (old.isEmpty()) return false;
        data.setCapeAccessory(ItemStack.EMPTY); // clear before returning: repeated requests cannot duplicate
        returnItem(player, old);
        changed(player);
        return true;
    }

    private static void returnItem(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void changed(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, .7f, 1f);
        player.getInventory().setChanged();
        PlayerDataEvents.sync(player);
    }

    /** One-time extraction for a character upgrading with an existing Piccolo top; never consumes their clothing. */
    public static void migrate(ServerPlayer player) {
        var data = ModCapabilities.getOrThrow(player);
        if (data.capeMigrationDone()) return;
        data.finishCapeMigration();
        boolean ownsTop = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof GiArmorItem gi && gi.set() == GiArmorItem.Set.NAMEKIAN
                    && gi.getEquipmentSlot() == net.minecraft.world.entity.EquipmentSlot.CHEST) { ownsTop = true; break; }
        }
        if (ownsTop && !data.hasCapeAccessory()) data.setCapeAccessory(new ItemStack(ModItems.PICCOLO_CAPE.get()));
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.dbzenith.piccolo_cape.hint").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** No inventory or item data comes from the client. */
    public record Remove() {
        public static void encode(Remove packet, FriendlyByteBuf buf) {}
        public static Remove decode(FriendlyByteBuf buf) { return new Remove(); }
        public static void handle(Remove packet, Supplier<NetworkEvent.Context> context) {
            var player = context.get().getSender();
            if (player != null && player.containerMenu == player.inventoryMenu) remove(player);
        }
    }
}
