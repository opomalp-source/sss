package com.dbzenith.item;

import com.dbzenith.DBZenith;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * Head clothing, worn on the head (the clothing rework; the user: "head clothing, for example the turbans from
 * Piccolo, as its own item"): drawn in 3D over the head by {@code GearLayer}, never as vanilla's flat helmet.
 * <ul>
 *   <li><b>Piccolo's Turban:</b> ivory wraps around a low purple crown; it covers the hair.</li>
 *   <li><b>Bandana:</b> Pan's orange bandana, knotted at the back.</li>
 *   <li><b>Headband:</b> a red fighter's headband, its tails hanging at the back.</li>
 * </ul>
 * Cloth: a point of armour, nothing more.
 */
public class HeadwearItem extends ArmorItem {
    public enum Kind {
        TURBAN(0xF4F2EC, 0x6A3A9A), BANDANA(0xF07820, 0xB04A10), HEADBAND(0xC8283A, 0x7A1420);

        /** Its colours: the cloth, and the band or the knot. */
        public final int color, accent;

        Kind(int color, int accent) {
            this.color = color;
            this.accent = accent;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Does it hide the hair under it? */
        public boolean coversHair() {
            return this == TURBAN;
        }
    }

    private static final ArmorMaterial CLOTH = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return 200; }
        @Override public int getDefenseForType(Type type) { return 1; }
        @Override public int getEnchantmentValue() { return 12; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.STRING); }
        @Override public String getName() { return DBZenith.MOD_ID + ":headwear"; }
        @Override public float getToughness() { return 0; }
        @Override public float getKnockbackResistance() { return 0; }
    };

    private final Kind kind;

    public HeadwearItem(Kind kind, Properties properties) {
        super(CLOTH, Type.HELMET, properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity, EquipmentSlot slot, String type) {
        return DBZenith.MOD_ID + ":textures/models/armor/" + kind.id() + "_layer_1.png";
    }

    /** What a player wears on the head, or null. */
    public static Kind worn(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof HeadwearItem h ? h.kind : null;
    }

    /** Drawn in 3D by GearLayer on players, not as the vanilla helmet layer. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.dbzenith.client.render.GearLayer.EXTENSIONS);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dbzenith." + kind.id() + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
