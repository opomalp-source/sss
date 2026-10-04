package com.dbzenith.race;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/**
 * Saiyan tails can be cut off by blades (and a Great Ape shrinks back at once), and grow back after a few days.
 */
public final class TailRules {
    private TailRules() {}

    public static boolean isBlade(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    /** A melee hit landed on a player: maybe it cut the tail. Returns true if it did. */
    public static boolean onHit(Player victim, PlayerData d, DamageSource source) {
        if (!d.hasTail() || !Races.of(d.getRace()).tail()) return false;
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return false;
        if (!isBlade(attacker.getMainHandItem())) return false;
        if (victim.getRandom().nextDouble() >= DBZConfig.SERVER.tailCutChance.get()) return false;
        cut(victim, d);
        return true;
    }

    public static void cut(Player victim, PlayerData d) {
        d.setTail(false);
        d.setTailCutAt(victim.level().getGameTime());
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1f, 0.8f);
        victim.displayClientMessage(Component.translatable("message.dbzenith.tail_cut"), true);
    }

    /** Once per second: a cut tail grows back in time. */
    public static void tick(ServerPlayer player, PlayerData d) {
        if (d.hasTail() || d.getTailCutAt() == PlayerData.TAIL_NOT_CUT || !Races.of(d.getRace()).tail()) return;
        if (player.level().getGameTime() - d.getTailCutAt() >= DBZConfig.SERVER.tailRegrowTicks.get()) {
            d.setTail(true);
            d.setTailCutAt(PlayerData.TAIL_NOT_CUT);
            player.displayClientMessage(Component.translatable("message.dbzenith.tail_regrown"), true);
        }
    }
}
