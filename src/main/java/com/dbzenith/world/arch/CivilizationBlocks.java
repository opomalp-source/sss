package com.dbzenith.world.arch;

import com.dbzenith.DBZenith;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The civilizations' building blocks (docs/WORLD_STRUCTURES.md): Capsule Corporation / Earth, Saiyan, Namekian, Frieza
 * Force and divine families, registered from the generated {@link CivilizationBlockList} with their stairs, slabs and
 * walls, a rounded corner piece per family ({@link RoundCorner}: a quarter-round column, so curved walls read as curved)
 * and sliding doors anyone (residents included) can open. Their own creative tab. Structures look blocks up by id
 * through {@link #get(String)}.
 */
public final class CivilizationBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DBZenith.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DBZenith.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DBZenith.MOD_ID);

    /** The doors' sounds: they slide (iron sounds), but open by hand. */
    static final BlockSetType SLIDING = BlockSetType.register(new BlockSetType("dbzenith_sliding", true, SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF,
            SoundEvents.STONE_BUTTON_CLICK_ON));

    private static final Map<String, RegistryObject<Block>> BY_ID = new LinkedHashMap<>();

    static {
        for (CivilizationBlockList.Spec s : CivilizationBlockList.ALL) {
            RegistryObject<Block> base = register(s.id(), () -> base(s), s.shape() == CivilizationBlockList.Shape.DOOR);
            if (s.stairs()) register(s.id() + "_stairs", () -> new StairBlock(() -> base.get().defaultBlockState(), props(s).noOcclusion()), false);
            if (s.slab()) register(s.id() + "_slab", () -> new SlabBlock(props(s)), false);
            if (s.wall()) register(s.id() + "_wall", () -> new WallBlock(props(s).forceSolidOn()), false);
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("civilizations", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dbzenith.civilizations"))
            .icon(() -> new ItemStack(get("capsule_logo").getBlock()))
            .displayItems((params, out) -> BY_ID.values().forEach(b -> out.accept(b.get())))
            .withTabsBefore(new net.minecraft.resources.ResourceLocation(DBZenith.MOD_ID, "architecture"))
            .build());

    private CivilizationBlocks() {}

    private static RegistryObject<Block> register(String id, Supplier<Block> block, boolean door) {
        RegistryObject<Block> obj = BLOCKS.register(id, block);
        ITEMS.register(id, () -> door ? new DoubleHighBlockItem(obj.get(), new Item.Properties()) : new BlockItem(obj.get(), new Item.Properties()));
        BY_ID.put(id, obj);
        return obj;
    }

    private static Block base(CivilizationBlockList.Spec s) {
        BlockBehaviour.Properties p = props(s);
        return switch (s.shape()) {
            case PILLAR -> new RotatedPillarBlock(p);
            case GLASS -> new GlassBlock(p.noOcclusion().isViewBlocking((st, l, pos) -> false).isSuffocating((st, l, pos) -> false));
            case LAMP -> new Block(p.lightLevel(st -> 15));
            case CORNER -> new RoundCorner(p.noOcclusion());
            case DOOR -> new SlidingDoor(p.noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY), SLIDING);
            default -> new Block(p);
        };
    }

    /** Hardness, sound and tool by material (the landmark set's, so the two sit together). */
    private static BlockBehaviour.Properties props(CivilizationBlockList.Spec s) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(s.color());
        return switch (s.material()) {
            case STONE -> p.strength(2f, 6f).requiresCorrectToolForDrops().sound(SoundType.STONE);
            case HARD -> p.strength(4f, 12f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK);
            case CLAY -> p.strength(1.5f, 5f).requiresCorrectToolForDrops().sound(SoundType.DECORATED_POT);
            case METAL -> p.strength(3f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL);
            case CLOTH -> p.strength(0.8f).sound(SoundType.WOOL);
            case GLASS -> p.strength(0.6f).sound(SoundType.GLASS);
            case LAMP -> p.strength(1.5f).requiresCorrectToolForDrops().sound(SoundType.GLASS);
            case SAND -> p.strength(0.6f).sound(SoundType.SAND);
        };
    }

    /**
     * A rounded corner: a quarter-round column filling the block's corner (its model is eight steps of a circle of
     * radius 16 about the south-east edge, turned by {@code facing}); its collision is the same steps, four of them.
     */
    public static final class RoundCorner extends HorizontalDirectionalBlock {
        private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

        static {
            VoxelShape north = Shapes.or(Block.box(0, 0, 8, 4, 16, 16), Block.box(4, 0, 4, 8, 16, 16), Block.box(8, 0, 1, 12, 16, 16), Block.box(12, 0, 0, 16, 16, 16));
            SHAPES.put(Direction.NORTH, north);
            SHAPES.put(Direction.EAST, rotate(north, 1));
            SHAPES.put(Direction.SOUTH, rotate(north, 2));
            SHAPES.put(Direction.WEST, rotate(north, 3));
        }

        /** {@code north} turned clockwise (seen from above) {@code quarters} times, as the blockstate's y rotation turns the model. */
        private static VoxelShape rotate(VoxelShape s, int quarters) {
            VoxelShape out = s;
            for (int i = 0; i < quarters; i++) {
                VoxelShape[] acc = {Shapes.empty()};
                out.forAllBoxes((x0, y0, z0, x1, y1, z1) -> acc[0] = Shapes.or(acc[0], Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1)));
                out = acc[0];
            }
            return out;
        }

        public RoundCorner(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        /** Placed with its round side toward the player. */
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPES.get(state.getValue(FACING));
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rot) {
            return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }
    }

    /**
     * A sliding door: shut, a door like any other; open, it has slid into the wall and stops nobody (a swinging leaf
     * stood across the doorway of anyone pressed against it on a slant, as residents leaving a block of flats together were).
     */
    public static final class SlidingDoor extends DoorBlock {
        public SlidingDoor(Properties p, BlockSetType type) {
            super(p, type);
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return state.getValue(OPEN) ? Shapes.empty() : super.getCollisionShape(state, level, pos, ctx);
        }
    }

    /** A block of the set by id ("capsule_white", "saiyan_metal_stairs"...). */
    public static BlockState get(String id) {
        RegistryObject<Block> b = BY_ID.get(id);
        if (b == null) throw new IllegalArgumentException("No civilization block " + id);
        return b.get().defaultBlockState();
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
