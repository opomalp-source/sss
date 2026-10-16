package com.dbzenith.transform;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Destruction;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.item.GiArmorItem;
import com.dbzenith.item.HeadwearItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * What a giant form (the Great Ape, the Giant Namekian; form scale 2 and up) does to the body and the world (v0.134).
 * <ul>
 *   <li><b>A giant's reach:</b> it steps up over what a person would climb (step height grows with the size), reaches
 *       as far as its arms, and strides faster (the view's widening is capped by {@code FlightPhysics}).</li>
 *   <li><b>Its weight:</b> it can't suffocate with its head in a cliff, and falls hurt it only from a height in
 *       proportion to its size.</li>
 *   <li><b>Trampling</b> (server config {@code destruction.giantTrample}): trees and plants it walks into give way,
 *       leaves, plants and snow crushed, logs dropping as wood; never in safe zones, and protection mods are asked.</li>
 *   <li><b>Clothes</b> (the user: "the clothes get destroyed when transforming, except clothes designed for it like
 *       Vegeta's"): becoming a Great Ape tears apart whatever cloth it wears; the battle armour of the Saiyans and the
 *       Frieza Force stretches and stays on, and metal armour bursts off into the inventory (or onto the ground).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GiantBody {
    private GiantBody() {}

    /** The smallest form scale that counts as a giant. */
    public static final float GIANT = 2f;
    private static final UUID STEP = UUID.fromString("6f3c1a0e-5d2b-4f4e-9a51-3e0b7c2d1a01");
    private static final UUID REACH = UUID.fromString("6f3c1a0e-5d2b-4f4e-9a51-3e0b7c2d1a02");
    private static final UUID BLOCK_REACH = UUID.fromString("6f3c1a0e-5d2b-4f4e-9a51-3e0b7c2d1a03");
    private static final UUID STRIDE = UUID.fromString("6f3c1a0e-5d2b-4f4e-9a51-3e0b7c2d1a04");
    /** At most this many blocks trampled a tick, per giant. */
    private static final int TRAMPLE_BUDGET = 48;

    public static float scale(Player player) {
        return GreatApe.scaleOf(player);
    }

    /** Every server tick: the giant's step, reach and stride follow its size (and go back to normal with it). */
    public static void tick(ServerPlayer player) {
        float s = scale(player);
        boolean giant = s >= GIANT;
        set(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP, "dbzenith giant step", giant ? 0.6 * (s - 1) : 0, AttributeModifier.Operation.ADDITION);
        set(player, ForgeMod.ENTITY_REACH.get(), REACH, "dbzenith giant reach", giant ? 0.8 * (s - 1) : 0, AttributeModifier.Operation.ADDITION);
        set(player, ForgeMod.BLOCK_REACH.get(), BLOCK_REACH, "dbzenith giant reach", giant ? 0.8 * (s - 1) : 0, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.MOVEMENT_SPEED, STRIDE, "dbzenith giant stride", giant ? 0.12 * (s - 1) : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (!giant) LAST.remove(player.getUUID());
        else if (player.tickCount % 2 == 0) trample(player, s);
    }

    private static void set(Player player, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance a = player.getAttribute(attribute);
        if (a == null) return;
        AttributeModifier now = a.getModifier(id);
        if (now != null && Math.abs(now.getAmount() - amount) < 1e-3) return;
        if (now != null) a.removeModifier(id);
        if (amount != 0) a.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    // ------------------------------------------------------------------ trampling

    /** Where each giant was at its last trample (a player moves itself, so its own motion isn't known here). */
    private static final java.util.Map<UUID, Vec3> LAST = new java.util.HashMap<>();

    /** Trees and plants in the giant's way, a little ahead of where it walks, give way. */
    private static void trample(ServerPlayer player, float s) {
        if (!DBZConfig.SERVER.destructionEnabled.get() || !DBZConfig.SERVER.giantTrample.get()) return;
        Vec3 last = LAST.put(player.getUUID(), player.position());
        if (last == null) return;
        Vec3 v = player.position().subtract(last).multiply(0.5, 0, 0.5);          // its walk over the last tick
        if (v.horizontalDistanceSqr() < 1e-4 || v.horizontalDistanceSqr() > 16) return;
        ServerLevel level = player.serverLevel();
        if (Destruction.isProtected(level, player.position())) return;
        AABB box = player.getBoundingBox().expandTowards(v.x * 4, 0, v.z * 4).inflate(0.3, 0, 0.3);
        int budget = TRAMPLE_BUDGET;
        int x0 = (int) Math.floor(box.minX), x1 = (int) Math.floor(box.maxX), z0 = (int) Math.floor(box.minZ), z1 = (int) Math.floor(box.maxZ);
        int y0 = (int) Math.floor(box.minY), y1 = (int) Math.floor(box.maxY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1 && budget > 0; y++) {
            for (int x = x0; x <= x1 && budget > 0; x++) {
                for (int z = z0; z <= z1 && budget > 0; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    int kind = trampled(state);
                    if (kind == 0) continue;
                    if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), state, player))) continue;
                    level.destroyBlock(pos, kind == 2, player);
                    budget--;
                }
            }
        }
    }

    /** 0 stands, 1 crushed (no drops), 2 broken with its drops (wood). */
    public static int trampled(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) return 0;
        if (state.is(BlockTags.LOGS)) return 2;
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.VINE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(BlockTags.SNOW)) {
            return state.liquid() || !state.getFluidState().isEmpty() ? 0 : 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ its weight

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && event.getSource().is(DamageTypes.IN_WALL) && scale(p) >= GIANT) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        float s = scale(p);
        if (s >= GIANT) event.setDistance(event.getDistance() / (s * 0.5f));   // a drop a person would feel, in proportion
    }

    // ------------------------------------------------------------------ clothes

    /** Clothes made to stretch with a Saiyan who turns into a Great Ape: the Saiyans' and the Frieza Force's battle armour. */
    public static boolean stretches(ItemStack stack) {
        return stack.getItem() instanceof GiArmorItem gi && (gi.set() == GiArmorItem.Set.BATTLE_ARMOR || gi.set() == GiArmorItem.Set.FROST_ARMOR);
    }

    /** Cloth: the mod's outfits and head clothing, and leather. It tears. */
    static boolean cloth(ItemStack stack) {
        return stack.getItem() instanceof GiArmorItem || stack.getItem() instanceof HeadwearItem
                || (stack.getItem() instanceof ArmorItem a && a.getMaterial() == ArmorMaterials.LEATHER);
    }

    /**
     * Becoming a Great Ape: whatever cloth the player wears tears apart (the item breaks, with the sound and the
     * scraps), battle armour stretches and stays on, and anything else worn (metal armour, a pumpkin) bursts off into
     * the inventory, or onto the ground when it's full. Returns how many pieces tore.
     */
    public static int tearClothes(ServerPlayer player) {
        int torn = 0, off = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || stretches(stack)) continue;
            if (cloth(stack)) {
                player.broadcastBreakEvent(slot);                                   // the tearing sound and scraps of it flying
                player.setItemSlot(slot, ItemStack.EMPTY);
                torn++;
            } else {
                player.setItemSlot(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                off++;
            }
        }
        if (torn > 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 2.5f, 0.6f);
            player.displayClientMessage(Component.translatable("message.dbzenith.clothes_torn"), false);
        }
        if (off > 0) player.displayClientMessage(Component.translatable("message.dbzenith.armour_burst"), false);
        return torn;
    }

    /** Called when a form is entered: a Great Ape's change tears the clothes (not when it was a giant already). */
    public static void onFormEntered(ServerPlayer player, Form from, Form to) {
        ApeStomp.entered(player, from, to);
        if (GreatApe.isApeForm(to.id()) && !GreatApe.isApeForm(from.id())) tearClothes(player);
        player.refreshDimensions();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (ModCapabilities.get(p).isPresent()) tick(p);
    }
}
