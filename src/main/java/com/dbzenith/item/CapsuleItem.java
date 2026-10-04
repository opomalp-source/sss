package com.dbzenith.item;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A capsule: right-click to pop open 27 slots of storage carried inside the item.
 * Capsules can't be nested; any capsule put inside is handed back on close.
 */
public class CapsuleItem extends Item {
    public static final int SIZE = 27;
    private static final String TAG = "capsuleItems";

    public CapsuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            SimpleContainer container = load(stack);
            container.addListener(c -> save(stack, (SimpleContainer) c));
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x3, id, inv, container, 3) {
                @Override
                public void removed(Player p2) {
                    super.removed(p2);
                    ejectNested(container, p2);
                    save(stack, container);
                }
            }, stack.getHoverName()));
            level.playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static SimpleContainer load(ItemStack stack) {
        SimpleContainer container = new SimpleContainer(SIZE);
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items);
            for (int i = 0; i < SIZE; i++) container.setItem(i, items.get(i));
        }
        return container;
    }

    public static void save(ItemStack stack, SimpleContainer container) {
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) items.set(i, container.getItem(i));
        ContainerHelper.saveAllItems(stack.getOrCreateTagElement(TAG), items, true);
    }

    /** No capsules inside capsules: give them back to the player. */
    public static void ejectNested(SimpleContainer container, Player player) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = container.getItem(i);
            if (s.getItem() instanceof CapsuleItem) {
                container.setItem(i, ItemStack.EMPTY);
                if (!player.getInventory().add(s)) player.drop(s, false);
                player.displayClientMessage(Component.translatable("message.dbzenith.capsule_nested"), true);
            }
        }
    }
}
