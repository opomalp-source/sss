package com.dbzenith.race;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.NeutralFighter;
import com.dbzenith.npc.QuestGiverEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Good and evil (-100..100). Deeds move it: slaying monsters and villains is good, killing the innocent is evil.
 * Good fighters regenerate ki faster; evil ones hit harder. The Galactic Patrol only works with the good.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Alignment {
    public static final int GOOD_AT = 30;
    public static final int EVIL_AT = -30;

    public enum Standing {
        GOOD(ChatFormatting.AQUA), NEUTRAL(ChatFormatting.GRAY), EVIL(ChatFormatting.RED);

        public final ChatFormatting color;

        Standing(ChatFormatting color) {
            this.color = color;
        }

        public String translationKey() {
            return "alignment.dbzenith." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Alignment() {}

    public static Standing of(PlayerData d) {
        if (d.getAlignment() >= GOOD_AT) return Standing.GOOD;
        if (d.getAlignment() <= EVIL_AT) return Standing.EVIL;
        return Standing.NEUTRAL;
    }

    /** Ki regeneration multiplier (good fighters). */
    public static double kiRegenMultiplier(PlayerData d) {
        return of(d) == Standing.GOOD ? 1.0 + DBZConfig.SERVER.alignmentBonus.get() : 1.0;
    }

    /** Outgoing damage multiplier (evil fighters). */
    public static double damageMultiplier(PlayerData d) {
        return of(d) == Standing.EVIL ? 1.0 + DBZConfig.SERVER.alignmentBonus.get() : 1.0;
    }

    /** How much killing {@code victim} moves the killer's alignment. */
    public static double shiftFor(LivingEntity victim) {
        if (victim instanceof QuestGiverEntity) return -20;
        if (victim instanceof AbstractVillager || victim instanceof IronGolem || victim instanceof NeutralFighter) return -5;
        if (victim instanceof ServerPlayer p) {
            PlayerData vd = ModCapabilities.get(p).orElse(null);
            if (vd == null) return 0;
            return switch (of(vd)) {
                case GOOD -> -10;
                case EVIL -> 5;
                case NEUTRAL -> -2;
            };
        }
        if (victim instanceof Enemy) return 0.25;
        return 0;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == event.getEntity()) return;
        double shift = shiftFor(event.getEntity());
        if (shift == 0) return;
        ModCapabilities.get(killer).ifPresent(d -> d.addAlignment(shift));
    }
}
