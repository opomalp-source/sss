package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.item.GiArmorItem;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.GiantBody;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Registry/save/crafting coverage for the separate armor cut, and shipped texture completeness. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WardrobeTests {
    private static ItemStack item(String id) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(DBZenith.MOD_ID, id)));
    }

    @GameTest(template = "empty", batch = "wardrobe")
    public static void armorCutsStayDistinctAndBothStretch(GameTestHelper h) {
        var p = TestPlayers.create(h);
        var d = ModCapabilities.getOrThrow(p);
        d.setRace(Race.SAIYAN); d.recomputeIfStale(); d.refill();
        var slots = new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        var names = new String[]{"top", "pants", "boots"};
        for (int i = 0; i < slots.length; i++) {
            String id = "cell_armor_" + names[i];
            ItemStack stack = item(id);
            h.assertTrue(stack.getItem() instanceof GiArmorItem gi && gi.set() == GiArmorItem.Set.CELL_ARMOR, "registered separate cut " + id);
            h.assertTrue(GiantBody.stretches(stack), "new armor stretches " + id);
            stack.setDamageValue(12);
            ItemStack restored = ItemStack.of(stack.save(new CompoundTag()));
            h.assertTrue(restored.is(stack.getItem()) && restored.getDamageValue() == 12, "identity and wear survive saving");
            p.setItemSlot(slots[i], restored);
            var recipe = h.getLevel().getRecipeManager().byKey(new ResourceLocation(DBZenith.MOD_ID, id));
            h.assertTrue(recipe.isPresent() && recipe.get().getResultItem(h.getLevel().registryAccess()).is(stack.getItem()), "craftable " + id);
        }
        h.assertTrue(GiArmorItem.wornSet(p) == GiArmorItem.Set.CELL_ARMOR, "complete new set recognized");
        p.setItemSlot(EquipmentSlot.FEET, item("battle_armor_boots"));
        h.assertTrue(GiArmorItem.wornSet(p) == null, "armor cuts cannot accidentally combine into one full-set bonus");
        p.setItemSlot(EquipmentSlot.FEET, item("cell_armor_boots"));
        FormHandler.enter(p, d, Forms.GREAT_APE);
        h.assertTrue(GiArmorItem.wornSet(p) == GiArmorItem.Set.CELL_ARMOR, "real Great Ape entry preserves all three pieces");
        h.assertTrue(GiantBody.stretches(item("battle_armor_top")), "old saved armor still stretches");
        TestPlayers.remove(h, p); h.succeed();
    }

    @GameTest(template = "empty", batch = "wardrobe")
    public static void completeOutfitResources(GameTestHelper h) throws Exception {
        for (var set : GiArmorItem.Set.values()) {
            for (String piece : new String[]{"top", "top_shell", "pants", "boots"}) {
                String path = "/assets/dbzenith/textures/entity/gear/" + set.id() + "_" + piece + ".png";
                try (var in = WardrobeTests.class.getResourceAsStream(path)) {
                    h.assertTrue(in != null, "shipped worn texture " + path);
                    var image = javax.imageio.ImageIO.read(in);
                    h.assertTrue(image != null && image.getWidth() == 512 && image.getHeight() == 512, "consistent UV resolution " + path);
                    boolean visible = false;
                    for (int y = 0; y < 512 && !visible; y++) for (int x = 0; x < 512; x++) if ((image.getRGB(x,y) >>> 24) != 0) { visible = true; break; }
                    h.assertTrue(visible, "nonempty material " + path);
                    if (piece.equals("boots")) h.assertTrue((image.getRGB(48,156) >>> 24) == 0,
                            "boot material must never appear on thigh caps: " + set.id());
                    if (piece.equals("pants")) h.assertTrue((image.getRGB(48,156) >>> 24) != 0,
                            "trouser material covers the whole thigh cap: " + set.id());
                }
            }
            for (String piece : new String[]{"top", "pants", "boots"}) {
                try (var in = WardrobeTests.class.getResourceAsStream("/assets/dbzenith/textures/item/" + set.id() + "_" + piece + ".png")) {
                    h.assertTrue(in != null, "shipped icon " + set.id() + piece);
                    h.assertTrue(javax.imageio.ImageIO.read(in).getWidth() == 64, "detailed icon " + set.id() + piece);
                }
            }
        }
        for(int layer=1;layer<=2;layer++) try(var in=WardrobeTests.class.getResourceAsStream("/assets/dbzenith/textures/models/armor/cell_armor_layer_"+layer+".png")) {
            h.assertTrue(in != null && javax.imageio.ImageIO.read(in) != null,"Classic/NPC armor fallback " + layer);
        }
        for (var kind : com.dbzenith.item.HeadwearItem.Kind.values()) {
            var stack = new ItemStack(com.dbzenith.registry.ModItems.HEADWEAR.get(kind).get());
            var hat = (com.dbzenith.item.HeadwearItem) stack.getItem();
            String texture = hat.getArmorTexture(stack, null, EquipmentSlot.HEAD, null);
            try (var in = WardrobeTests.class.getResourceAsStream("/assets/" + texture.replace(':','/'))) {
                h.assertTrue(in != null && javax.imageio.ImageIO.read(in) != null, "headwear fallback resolves " + kind);
            }
        }
        h.succeed();
    }
}
