package com.dbzenith.race;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.BossFighter;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueLibrary;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Namekian fusion and Majin absorption.
 * <ul>
 *   <li><b>Fusion</b> (Namekian): with a beaten Namekian Warrior (it joins you: + points to STR/CON/KI_POWER/SPI), or with
 *   another Namekian player who accepts. They become part of you: you gain a share of their attributes and their
 *   techniques, and their character starts over. Limited number of fusions.</li>
 *   <li><b>Absorption</b> (Majin): a beaten creature or player (not a boss) is absorbed. Each one adds a temporary
 *   STR/DEX/KI_POWER multiplier stack; a player also gives you one technique you did not know.</li>
 * </ul>
 */
public final class Absorption {
    /** Fusion requests: partner (asked) -> requester and expiry. */
    private record Request(UUID requester, long expires) {}

    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final long REQUEST_TICKS = 600;
    private static final Vector3f MAJIN_PINK = new Vector3f(1f, 0.45f, 0.75f);
    private static final Vector3f NAMEK_GREEN = new Vector3f(0.45f, 0.95f, 0.4f);

    private Absorption() {}

    public static boolean beaten(LivingEntity e) {
        return e.getHealth() <= e.getMaxHealth() * DBZConfig.SERVER.absorbHealthThreshold.get() + 1e-4;
    }

    // ------------------------------------------------------------------ Namekian fusion

    public static boolean fuse(ServerPlayer player, PlayerData data, LivingEntity target) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (data.getRace() != Race.NAMEKIAN) return false;
        if (data.getFusions() >= c.fusionMax.get()) {
            player.displayClientMessage(Component.translatable("message.dbzenith.fusion_limit"), true);
            return false;
        }
        if (target instanceof KiFighter warrior && warrior.getType() == ModNpcs.NAMEKIAN_WARRIOR.get()) {
            if (!beaten(warrior)) {
                player.displayClientMessage(Component.translatable("message.dbzenith.fusion_not_beaten"), true);
                return false;
            }
            int points = 0;
            for (Attribute a : List.of(Attribute.STRENGTH, Attribute.CONSTITUTION, Attribute.KI_POWER, Attribute.SPIRIT)) {
                int gain = fusionGain(data.getAttribute(a));
                data.setAttribute(a, data.getAttribute(a) + gain);
                points += gain;
            }
            data.addFusion();
            burst(player.serverLevel(), warrior.position(), NAMEK_GREEN);
            warrior.discard();
            player.displayClientMessage(Component.translatable("message.dbzenith.fused_warrior", points).withStyle(ChatFormatting.GREEN), false);
            return true;
        }
        if (target instanceof ServerPlayer partner) {
            PlayerData pd = ModCapabilities.get(partner).orElse(null);
            if (pd == null || pd.getRace() != Race.NAMEKIAN) {
                player.displayClientMessage(Component.translatable("message.dbzenith.fusion_needs_namekian"), true);
                return false;
            }
            REQUESTS.put(partner.getUUID(), new Request(player.getUUID(), player.level().getGameTime() + REQUEST_TICKS));
            Component accept = Component.translatable("message.dbzenith.fusion_accept_button").withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN).withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dbzfusion accept")));
            partner.sendSystemMessage(Component.translatable("message.dbzenith.fusion_request", player.getDisplayName()).append(" ").append(accept));
            player.displayClientMessage(Component.translatable("message.dbzenith.fusion_asked", partner.getDisplayName()), true);
            return true;
        }
        player.displayClientMessage(Component.translatable("message.dbzenith.fusion_needs_namekian"), true);
        return false;
    }

    /** Points one attribute gains from fusing with a Namekian Warrior. */
    public static int fusionGain(int value) {
        return Math.max(3, (int) Math.round(value * DBZConfig.SERVER.fusionNpcShare.get()));
    }

    /** The asked player agrees: they become part of the one who asked. Returns false if there is no valid request. */
    public static boolean acceptFusion(ServerPlayer partner) {
        Request r = REQUESTS.remove(partner.getUUID());
        if (r == null || partner.level().getGameTime() > r.expires) return false;
        ServerPlayer host = partner.server.getPlayerList().getPlayer(r.requester);
        if (host == null || host.level() != partner.level() || host.distanceToSqr(partner) > 16 * 16) return false;
        PlayerData hd = ModCapabilities.get(host).orElse(null);
        PlayerData pd = ModCapabilities.get(partner).orElse(null);
        if (hd == null || pd == null || hd.getRace() != Race.NAMEKIAN || pd.getRace() != Race.NAMEKIAN) return false;
        DBZConfig.Server c = DBZConfig.SERVER;
        if (hd.getFusions() >= c.fusionMax.get()) return false;
        double share = c.fusionAttributeShare.get();
        for (Attribute a : Attribute.values()) {
            hd.setAttribute(a, hd.getAttribute(a) + (int) Math.round(pd.getAttribute(a) * share));
        }
        for (String id : pd.learnedView()) TechniqueLibrary.learnFree(hd, Techniques.byId(id));
        hd.addFusion();
        burst(partner.serverLevel(), partner.position(), NAMEK_GREEN);
        pd.reset(); // the partner lives on inside the host; their own story starts over
        host.sendSystemMessage(Component.translatable("message.dbzenith.fused_player", partner.getDisplayName()).withStyle(ChatFormatting.GREEN));
        partner.sendSystemMessage(Component.translatable("message.dbzenith.fused_into", host.getDisplayName()).withStyle(ChatFormatting.GREEN));
        return true;
    }

    // ------------------------------------------------------------------ Majin absorption

    public static boolean absorb(ServerPlayer player, PlayerData data, LivingEntity target) {
        if (data.getRace() != Race.MAJIN) return false;
        if (target != null && !com.dbzenith.combat.PvpRules.mayAffect(player, target)) return false;   // PvP mode (CX-19)
        if (target == null || target instanceof BossFighter || target.getType().is(Tags.EntityTypes.BOSSES)) {
            player.displayClientMessage(Component.translatable("message.dbzenith.absorb_nothing"), true);
            return false;
        }
        if (!beaten(target)) {
            player.displayClientMessage(Component.translatable("message.dbzenith.absorb_not_beaten"), true);
            return false;
        }
        DBZConfig.Server c = DBZConfig.SERVER;
        long now = player.level().getGameTime();
        Component gained = null;
        if (target instanceof ServerPlayer victim && !player.canHarmPlayer(victim)) {
            player.displayClientMessage(Component.translatable("message.dbzenith.absorb_nothing"), true);
            return false; // PvP is off
        }
        if (target instanceof ServerPlayer victim) {
            PlayerData vd = ModCapabilities.get(victim).orElse(null);
            if (vd != null) {
                List<Technique> options = new ArrayList<>();
                for (String id : vd.learnedView()) {
                    Technique t = Techniques.byId(id);
                    if (t != null && !data.knows(id) && t.races().contains(data.getRace())) options.add(t);
                }
                if (!options.isEmpty()) {
                    Technique t = options.get(player.getRandom().nextInt(options.size()));
                    TechniqueLibrary.learnFree(data, t);
                    gained = Component.translatable(t.translationKey());
                }
            }
        }
        burst(player.serverLevel(), target.position(), MAJIN_PINK);
        target.hurt(com.dbzenith.combat.ModDamageTypes.absorbed(player.level(), player), Float.MAX_VALUE);
        data.addMajinStack(now, c.majinAbsorbMaxStacks.get(), c.majinAbsorbDurationTicks.get());
        player.displayClientMessage(Component.translatable("message.dbzenith.absorbed", data.getMajinStacks()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (gained != null) player.displayClientMessage(Component.translatable("message.dbzenith.absorbed_technique", gained), false);
        return true;
    }

    private static void burst(ServerLevel level, Vec3 at, Vector3f color) {
        level.sendParticles(new DustParticleOptions(color, 1.5f), at.x, at.y + 1, at.z, 60, 0.5, 0.9, 0.5, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 20, 0.3, 0.6, 0.3, 0.08);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.4f);
    }
}
