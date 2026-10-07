package com.dbzenith.item;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Plating a gi piece with metal (12e): one gi top, trousers or boots and one ingot at a crafting table give the piece
 * back plated with that metal (replacing any plating it had). Each plated piece worn adds the metal's
 * {@link Metal#platingReduction} to damage reduction, whether or not the set is complete.
 */
public class GiPlating extends CustomRecipe {
    public static final String TAG = "dbzenith_plating";

    public GiPlating(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    /** The metal a gi piece is plated with, or null. */
    public static Metal of(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG) ? Metal.byId(stack.getTag().getString(TAG)) : null;
    }

    public static ItemStack plate(ItemStack gi, Metal metal) {
        ItemStack out = gi.copyWithCount(1);
        out.getOrCreateTag().putString(TAG, metal.id());
        return out;
    }

    /** Damage reduction from the plating of every gi piece a player wears. */
    public static double reduction(Player player) {
        double sum = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = player.getItemBySlot(slot);
            Metal m = s.getItem() instanceof GiArmorItem ? of(s) : null;
            if (m != null) sum += m.platingReduction();
        }
        return sum;
    }

    private record Parts(ItemStack gi, Metal metal) {}

    private static Parts parts(CraftingContainer grid) {
        ItemStack gi = ItemStack.EMPTY;
        Metal metal = null;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof GiArmorItem && gi.isEmpty()) gi = s;
            else if (Metal.of(s.getItem()) != null && metal == null) metal = Metal.of(s.getItem());
            else return null;
        }
        return gi.isEmpty() || metal == null ? null : new Parts(gi, metal);
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return parts(grid) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        Parts p = parts(grid);
        return p == null ? ItemStack.EMPTY : plate(p.gi, p.metal);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        return NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.dbzenith.registry.ModRecipes.GI_PLATING.get();
    }
}
