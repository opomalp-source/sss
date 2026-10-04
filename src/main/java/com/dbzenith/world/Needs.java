package com.dbzenith.world;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SuspiciousStewItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Life-sim needs (each toggled in config {@code life_sim}): thirst and temperature. Both are gentle: they slow
 * stamina recovery, and only an empty thirst bar hurts (never below 10% body). Androids need neither; Frost Demons
 * ignore temperature.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Needs {
    public static final double THIRSTY_BELOW = 20;

    private Needs() {}

    /** Called once per second. */
    public static void tick(ServerPlayer player, PlayerData d) {
        DBZConfig.Server c = DBZConfig.SERVER;
        boolean immune = d.getRace() == Race.ANDROID || player.isCreative() || player.isSpectator();
        int temp = c.temperatureEnabled.get() && !immune && d.getRace() != Race.FROST_DEMON ? temperatureAt(player) : 0;
        d.setTemperature(temp);

        if (!c.thirstEnabled.get() || immune) {
            d.setThirst(100);
            return;
        }
        double perSecond = 100.0 / (c.thirstDaysToEmpty.get() * 1200.0);
        if (d.isCharging() || d.isFlying()) perSecond *= 2;
        if (temp > 0) perSecond *= 2;
        double t = d.getThirst() - perSecond;
        if (player.isInWater()) t += 2; // a mouthful while swimming
        d.setThirst(t);
        if (d.getThirst() <= 0) {
            double floor = d.getDerived().maxBody() * 0.1;
            if (d.getBody() > floor) d.setBody(Math.max(floor, d.getBody() - d.getDerived().maxBody() * 0.005));
        }
    }

    /** Stamina recovery is halved when thirsty and again when too hot or too cold. */
    public static double staminaRegenMultiplier(PlayerData d) {
        double m = 1.0;
        if (DBZConfig.SERVER.thirstEnabled.get() && d.getThirst() < THIRSTY_BELOW) m *= 0.5;
        if (d.getTemperature() != 0) m *= 0.5;
        return m;
    }

    /** -1 cold, 0 comfortable, 1 hot. */
    public static int temperatureAt(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimensionType().ultraWarm()) return 1;
        BlockPos pos = player.blockPosition();
        float base = level.getBiome(pos).value().getBaseTemperature();
        if (base < 0.15f) {
            boolean dressed = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
            return dressed || nearHeat(level, pos) ? 0 : -1;
        }
        if (base >= 1.5f && level.isDay() && level.canSeeSky(pos.above())) return 1;
        return 0;
    }

    private static boolean nearHeat(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, 2, 3))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.FIRE) || s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK)
                    || (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT))) return true;
        }
        return false;
    }

    /** How much drinking or eating {@code stack} quenches thirst. */
    public static double quench(ItemStack stack) {
        if (stack.is(Items.POTION)) return 40;
        if (stack.is(Items.MILK_BUCKET)) return 30;
        if (stack.getItem() instanceof BowlFoodItem || stack.getItem() instanceof SuspiciousStewItem) return 25;
        if (stack.is(Items.HONEY_BOTTLE)) return 20;
        if (stack.is(Items.MELON_SLICE)) return 10;
        if (stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES)) return 5;
        return 0;
    }

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        double q = quench(event.getItem());
        if (q > 0) ModCapabilities.get(player).ifPresent(d -> d.setThirst(d.getThirst() + q));
    }
}
