package com.dbzenith.dragonball;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.Races;
import com.dbzenith.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** What the Eternal Dragon can grant. Amounts come from the {@code dragon_balls} config section. */
public enum Wish {
    POWER,
    RESTORATION,
    SENZU,
    IMMORTALITY,
    HIDDEN_POTENTIAL,
    GODLY_KI,
    ETERNAL_YOUTH,
    RICHES;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "wish.dbzenith." + id();
    }

    public void grant(ServerPlayer player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        switch (this) {
            case POWER -> d.addTrainingPoints(c.wishPowerTp.get());
            case RESTORATION -> {
                d.refill();
                player.setHealth(player.getMaxHealth());
                if (Races.of(d.getRace()).tail()) d.setTail(true);
                player.removeAllEffects();
            }
            case SENZU -> give(player, new ItemStack(ModItems.SENZU_BEAN.get(), c.wishSenzuCount.get()));
            case IMMORTALITY -> d.setImmortalUntil(player.level().getGameTime() + c.wishImmortalityTicks.get());
            case HIDDEN_POTENTIAL -> d.setFlag("potential_unlocked", true);
            case GODLY_KI -> d.setFlag("god_ki", true);
            case ETERNAL_YOUTH -> {
                d.setPhysicalAge(20);
                d.recomputeIfStale();
            }
            case RICHES -> give(player, new ItemStack(Items.DIAMOND, c.wishDiamonds.get()));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
