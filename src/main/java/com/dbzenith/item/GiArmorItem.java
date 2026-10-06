package com.dbzenith.item;

import com.dbzenith.DBZenith;
import com.dbzenith.data.PlayerData;
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
 * Fighting clothes in sets of three (chest, legs, boots). Wearing a full set multiplies combat attributes
 * (see {@link Set#strMult()} etc.), applied through {@code FormMath} via {@link PlayerData#getGearMultiplier}.
 */
public class GiArmorItem extends ArmorItem {
    public enum Set {
        TURTLE(1.10, 1.10, 1.00, 0.05, 2, Items.LEATHER),
        DEMON(1.00, 1.05, 1.15, 0.05, 2, Items.LEATHER),
        BATTLE_ARMOR(1.08, 1.00, 1.08, 0.15, 5, Items.IRON_INGOT),
        NAMEKIAN(1.00, 1.04, 1.12, 0.08, 3, Items.LEATHER),
        MAJIN(1.12, 1.03, 1.00, 0.06, 2, Items.LEATHER),
        HOODIE(1.02, 1.08, 1.02, 0.03, 1, Items.LEATHER),
        FROST_ARMOR(1.04, 1.02, 1.12, 0.14, 5, Items.IRON_INGOT);

        private final double strMult;
        private final double dexMult;
        private final double kiMult;
        private final double reduction;
        private final ArmorMaterial material;

        Set(double str, double dex, double ki, double reduction, int defense, net.minecraft.world.item.Item repair) {
            this.reduction = reduction;
            strMult = str;
            dexMult = dex;
            kiMult = ki;
            String name = DBZenith.MOD_ID + ":" + name().toLowerCase(Locale.ROOT);
            material = new ArmorMaterial() {
                @Override public int getDurabilityForType(Type type) { return 300; }
                @Override public int getDefenseForType(Type type) { return type == Type.CHESTPLATE ? defense + 1 : defense; }
                @Override public int getEnchantmentValue() { return 12; }
                @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
                @Override public Ingredient getRepairIngredient() { return Ingredient.of(repair); }
                @Override public String getName() { return name; }
                @Override public float getToughness() { return 0; }
                @Override public float getKnockbackResistance() { return 0; }
            };
        }

        public double strMult() { return strMult; }
        public double dexMult() { return dexMult; }
        public double kiMult() { return kiMult; }
        /** Share of all incoming damage a full set takes off. */
        public double reduction() { return reduction; }
        public ArmorMaterial material() { return material; }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final EquipmentSlot[] SET_SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Set set;

    public GiArmorItem(Set set, Type type, Properties properties) {
        super(set.material(), type, properties);
        this.set = set;
    }

    public Set set() {
        return set;
    }

    /** Painted clothes and their 3D pieces on players (CX-14e), instead of the flat vanilla armour layers. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.dbzenith.client.render.GearLayer.EXTENSIONS);
    }

    /** The full set a player is wearing, or null. */
    public static Set wornSet(Player player) {
        Set found = null;
        for (EquipmentSlot slot : SET_SLOTS) {
            if (!(player.getItemBySlot(slot).getItem() instanceof GiArmorItem gi)) return null;
            if (found == null) found = gi.set;
            else if (found != gi.set) return null;
        }
        return found;
    }

    /** Called every tick from KiTicker. */
    public static void updateBonus(Player player, PlayerData data) {
        Set s = wornSet(player);
        if (s == null) data.setGearMultipliers(1, 1, 1);
        else data.setGearMultipliers(s.strMult(), s.dexMult(), s.kiMult());
        data.setGearReduction(s == null ? 0 : s.reduction());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dbzenith.gi.set_bonus",
                pct(set.strMult), pct(set.dexMult), pct(set.kiMult), Math.round(set.reduction * 100)).withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    private static String pct(double m) {
        return String.format("+%d%%", Math.round((m - 1) * 100));
    }
}
