package com.dbzenith.quest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.skill.TechniqueLibrary;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Quest rules. Objective checks are pure functions of the player and their data so the client can show the
 * same progress the server enforces; accepting and turning in are server-only and validated here.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestManager {
    /** Galactic Patrol reputation needed for each rank (index = rank). */
    public static final int[] RANK_REP = {0, 50, 150, 400, 1000};
    public static final String[] RANK_KEYS = {"recruit", "officer", "inspector", "elite", "commander"};

    private QuestManager() {}

    public static int patrolRank(PlayerData d) {
        int rank = 0;
        for (int i = 0; i < RANK_REP.length; i++) if (d.getPatrolRep() >= RANK_REP[i]) rank = i;
        return rank;
    }

    /** Null if the quest can be accepted now, otherwise why not (lang key). */
    public static String unavailableReason(PlayerData d, Quest q) {
        if (d.isQuestActive(q.id())) return "quest.dbzenith.why.active";
        if (d.timesCompleted(q.id()) > 0 && !q.repeatable()) return "quest.dbzenith.why.done";
        for (String req : q.requires()) if (d.timesCompleted(req) == 0) return "quest.dbzenith.why.requires";
        if (q.giver() == Quest.Giver.PATROL) {
            if (d.getAlignment() < 0) return "quest.dbzenith.why.alignment";
            if (patrolRank(d) < q.minRank()) return "quest.dbzenith.why.rank";
        }
        return null;
    }

    /** Current value of one objective (0..amount). */
    public static int progress(Player player, PlayerData d, Quest q, int index) {
        Quest.Objective o = q.objectives().get(index);
        int[] counted = d.questProgress(q.id());
        return switch (o.type()) {
            case KILL -> counted == null || index >= counted.length ? 0 : counted[index];
            case REACH_LEVEL -> Math.min(o.amount(), safeLevel(d));
            case LEARN_TECHNIQUE -> d.knows(o.target()) ? 1 : 0;
            case ANY_FORM -> d.hasFlag("has_transformed") ? 1 : 0;
            case COLLECT_ITEM -> Math.min(o.amount(), countItem(player, o.target()));
            case VISIT_DIMENSION -> d.hasFlag("visited:" + o.target()) ? 1 : 0;
            case HAS_FLAG -> d.hasFlag(o.target()) ? 1 : 0;
        };
    }

    public static boolean isComplete(Player player, PlayerData d, Quest q) {
        if (!d.isQuestActive(q.id())) return false;
        for (int i = 0; i < q.objectives().size(); i++) {
            if (progress(player, d, q, i) < q.objectives().get(i).amount()) return false;
        }
        return true;
    }

    private static int safeLevel(PlayerData d) {
        try {
            return StatCalculator.level(d);
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    private static int countItem(Player player, String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null) return 0;
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : player.getInventory().armor) if (s.is(item)) n += s.getCount();
        return n;
    }

    // ------------------------------------------------------------------ server actions

    public static boolean accept(ServerPlayer player, String id) {
        Quest q = Quests.byId(id);
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (q == null || d == null || unavailableReason(d, q) != null) return false;
        d.startQuest(q.id(), q.objectives().size());
        player.displayClientMessage(Component.translatable("message.dbzenith.quest_accepted", Component.translatable(q.titleKey())), true);
        return true;
    }

    public static boolean turnIn(ServerPlayer player, String id) {
        Quest q = Quests.byId(id);
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (q == null || d == null || !isComplete(player, d, q)) return false;
        d.finishQuest(q.id());
        Quest.Reward r = q.reward();
        if (r.tp() > 0) d.addTrainingPoints(r.tp());
        for (Quest.ItemReward ir : r.items()) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ir.itemId()));
            if (item == null) continue;
            ItemStack stack = new ItemStack(item, ir.count());
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        if (!r.flag().isEmpty()) d.setFlag(r.flag(), true);
        if (!r.technique().isEmpty()) TechniqueLibrary.learnFree(d, Techniques.byId(r.technique()));
        if (r.alignment() != 0) d.setAlignment(d.getAlignment() + r.alignment());
        if (r.patrolRep() > 0) {
            int before = patrolRank(d);
            d.addPatrolRep(r.patrolRep());
            int after = patrolRank(d);
            if (after > before) {
                player.displayClientMessage(Component.translatable("message.dbzenith.patrol_promoted",
                        Component.translatable("patrol.dbzenith.rank." + RANK_KEYS[after])), false);
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.dbzenith.quest_complete", Component.translatable(q.titleKey())), false);
        return true;
    }

    // ------------------------------------------------------------------ hooks

    /** Kill objectives (alignment shifts live in race.Alignment). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        PlayerData d = ModCapabilities.get(killer).orElse(null);
        if (d == null) return;
        ResourceLocation type = EntityType.getKey(event.getEntity().getType());
        for (String id : java.util.List.copyOf(d.activeQuestsView().keySet())) {
            Quest q = Quests.byId(id);
            if (q == null) continue;
            for (int i = 0; i < q.objectives().size(); i++) {
                Quest.Objective o = q.objectives().get(i);
                if (o.type() == Quest.Objective.Type.KILL && o.target().equals(type.toString())) d.addQuestProgress(id, i, 1, o.amount());
            }
        }
    }

    /** Called once per second from KiTicker: records dimension visits. */
    public static void tick(ServerPlayer player, PlayerData d) {
        d.setFlag("visited:" + player.level().dimension().location(), true);
    }
}
