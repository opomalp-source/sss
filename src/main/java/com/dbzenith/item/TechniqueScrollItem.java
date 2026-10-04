package com.dbzenith.item;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueLibrary;
import com.dbzenith.skill.Techniques;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A scroll that teaches one technique for free (race permitting). Technique id in NBT "technique". */
public class TechniqueScrollItem extends Item {
    public static final String TAG = "technique";

    public TechniqueScrollItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Item item, Technique t) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString(TAG, t.id());
        return stack;
    }

    public static Technique technique(ItemStack stack) {
        return stack.hasTag() ? Techniques.byId(stack.getTag().getString(TAG)) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        Technique t = technique(stack);
        return t == null ? super.getName(stack) : Component.translatable("item.dbzenith.technique_scroll.named", Component.translatable(t.translationKey()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Technique t = technique(stack);
        if (t == null) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            boolean learned = ModCapabilities.get(player).map(d -> TechniqueLibrary.learnFree(d, t)).orElse(false);
            if (learned) {
                player.displayClientMessage(Component.translatable("message.dbzenith.learned", Component.translatable(t.translationKey())), true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            } else {
                player.displayClientMessage(Component.translatable("message.dbzenith.cannot_learn"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
