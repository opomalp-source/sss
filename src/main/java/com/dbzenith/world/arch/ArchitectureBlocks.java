package com.dbzenith.world.arch;

import com.dbzenith.DBZenith;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The landmark block set (CX-33): every block the great structures are built from (the tournament's white stone and
 * temple, Kami's Lookout, Korin Tower, the Cell Games ring, the Frypan strata and West City), registered from the
 * generated {@link ArchitectureBlockList} with their stairs, slabs and walls, block items and their own creative tab.
 * The structures look blocks up by id through {@link #get(String)}, so the palette lives in one place.
 */
public final class ArchitectureBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DBZenith.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DBZenith.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DBZenith.MOD_ID);

    /** Every block of the set by id (bases and their _stairs, _slab and _wall), in table order. */
    private static final Map<String, RegistryObject<Block>> BY_ID = new LinkedHashMap<>();

    static {
        for (ArchitectureBlockList.Spec s : ArchitectureBlockList.ALL) {
            RegistryObject<Block> base = register(s.id(), () -> base(s));
            if (s.stairs()) register(s.id() + "_stairs", () -> new StairBlock(() -> base.get().defaultBlockState(), props(s).noOcclusion()));
            if (s.slab()) register(s.id() + "_slab", () -> new SlabBlock(props(s)));
            if (s.wall()) register(s.id() + "_wall", () -> new WallBlock(props(s).forceSolidOn()));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("architecture", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dbzenith.architecture"))
            .icon(() -> new ItemStack(get("temple_gold_trim").getBlock()))
            .displayItems((params, out) -> BY_ID.values().forEach(b -> out.accept(b.get())))
            .withTabsBefore(new net.minecraft.resources.ResourceLocation(DBZenith.MOD_ID, "main"))
            .build());

    private ArchitectureBlocks() {}

    private static RegistryObject<Block> register(String id, Supplier<Block> block) {
        RegistryObject<Block> obj = BLOCKS.register(id, block);
        ITEMS.register(id, () -> new BlockItem(obj.get(), new Item.Properties()));
        BY_ID.put(id, obj);
        return obj;
    }

    private static Block base(ArchitectureBlockList.Spec s) {
        BlockBehaviour.Properties p = props(s);
        boolean sky = s.id().startsWith(SKY_PREFIX);
        return switch (s.shape()) {
            case PILLAR -> sky ? new SkyPillar(p) : new RotatedPillarBlock(p);
            case GLASS -> new GlassBlock(p.noOcclusion().isViewBlocking((st, l, pos) -> false).isSuffocating((st, l, pos) -> false));
            case LAMP -> {
                p = p.lightLevel(st -> s.material() == ArchitectureBlockList.Material.GLASS ? 7 : 15);
                yield sky ? new SkyBlock(p) : new Block(p);
            }
            default -> sky ? new SkyBlock(p) : new Block(p);
        };
    }

    /**
     * Kami's Lookout's blocks let daylight through (CX-33): the island is a hundred blocks across, and solid it would
     * leave the land under it in endless night, full of monsters, with its own underside black. They are still
     * solid and opaque to the eye.
     */
    static final String SKY_PREFIX = "lookout_";

    static final class SkyBlock extends Block {
        SkyBlock(Properties p) {
            super(p);
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            return true;
        }

        @Override
        public int getLightBlock(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            return 0;
        }
    }

    static final class SkyPillar extends RotatedPillarBlock {
        SkyPillar(Properties p) {
            super(p);
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            return true;
        }

        @Override
        public int getLightBlock(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            return 0;
        }
    }

    /** Hardness, sound and tool by material: worked stone a little tougher than vanilla's, Korin's stone tougher still. */
    private static BlockBehaviour.Properties props(ArchitectureBlockList.Spec s) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(s.color());
        return switch (s.material()) {
            case STONE -> p.strength(2f, 6f).requiresCorrectToolForDrops().sound(SoundType.STONE);
            case HARD -> p.strength(4f, 12f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS);
            case CLAY -> p.strength(1.5f, 5f).requiresCorrectToolForDrops().sound(SoundType.DECORATED_POT);
            case METAL -> p.strength(3f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL);
            case CLOTH -> p.strength(0.8f).sound(SoundType.WOOL);
            case GLASS -> p.strength(0.6f).sound(SoundType.GLASS);
            case LAMP -> p.strength(1.5f).requiresCorrectToolForDrops().sound(SoundType.LANTERN);
            case SAND -> p.strength(0.6f).sound(SoundType.SAND);
        };
    }

    /** A block of the set by id ("tournament_stone", "clay_roof_tiles_stairs"...). */
    public static BlockState get(String id) {
        RegistryObject<Block> b = BY_ID.get(id);
        if (b == null) throw new IllegalArgumentException("No landmark block " + id);
        return b.get().defaultBlockState();
    }

    public static Block block(String id) {
        return get(id).getBlock();
    }

    public static Iterable<RegistryObject<Block>> all() {
        return BY_ID.values();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
