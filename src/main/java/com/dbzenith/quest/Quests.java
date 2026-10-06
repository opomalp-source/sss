package com.dbzenith.quest;

import com.dbzenith.quest.Quest.Objective;
import com.dbzenith.quest.Quest.Reward;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quest content. The Master's story line walks a new fighter through every system and ends with the
 * God Ki ritual; the Galactic Patrol offers repeatable bounties that build rank.
 */
public final class Quests {
    private static final Map<String, Quest> BY_ID = new LinkedHashMap<>();

    // ---------------------------------------------------------------- the Master's story
    static {
        add(new Quest("first_steps", Quest.Giver.MASTER, List.of(Objective.level(10)), List.of(), 0, false,
                Reward.tp(100).with("dbzenith:turtle_top", 1)));
        add(new Quest("energy_within", Quest.Giver.MASTER, List.of(Objective.learn("ki_sense")), List.of("first_steps"), 0, false,
                Reward.tp(150).with("dbzenith:senzu_bean", 2)));
        add(new Quest("trial_by_combat", Quest.Giver.MASTER, List.of(Objective.kill("dbzenith:sproutling", 5)), List.of("energy_within"), 0, false,
                Reward.tp(250).with("dbzenith:turtle_pants", 1).with("dbzenith:turtle_boots", 1)));
        add(new Quest("weight_of_the_world", Quest.Giver.MASTER, List.of(Objective.level(100), Objective.collect("dbzenith:training_weights", 1)),
                List.of("trial_by_combat"), 0, false, Reward.tp(600).with("dbzenith:heavy_training_weights", 1)));
        add(new Quest("beyond_limits", Quest.Giver.MASTER, List.of(Objective.anyForm()), List.of("weight_of_the_world"), 0, false,
                Reward.tp(1200).with("dbzenith:dragon_radar", 1)));
        add(new Quest("the_wish", Quest.Giver.MASTER, List.of(Objective.flag("summoned_dragon")), List.of("beyond_limits"), 0, false,
                Reward.tp(2000).with("dbzenith:time_chamber_door", 1)));
        add(new Quest("divine_ritual", Quest.Giver.MASTER, List.of(Objective.level(1000), Objective.visit("dbzenith:time_chamber")),
                List.of("the_wish"), 0, false, Reward.tp(4000).flag("god_ki")));
    }

    // ---------------------------------------------------------------- Galactic Patrol bounties (repeatable)
    static {
        add(new Quest("bounty_soldiers", Quest.Giver.PATROL, List.of(Objective.kill("dbzenith:ki_soldier", 10)), List.of(), 0, true,
                Reward.tp(150).rep(20, 5)));
        add(new Quest("bounty_androids", Quest.Giver.PATROL, List.of(Objective.kill("dbzenith:android_unit", 3)), List.of(), 1, true,
                Reward.tp(250).with("minecraft:iron_ingot", 8).rep(25, 5)));
        add(new Quest("bounty_tyrant", Quest.Giver.PATROL, List.of(Objective.kill("dbzenith:tyrant_lord", 1)), List.of(), 2, true,
                Reward.tp(1500).with("dbzenith:senzu_bean", 4).rep(100, 10)));
        add(new Quest("bounty_brute", Quest.Giver.PATROL, List.of(Objective.kill("dbzenith:rampage_brute", 1)), List.of(), 2, true,
                Reward.tp(1500).with("dbzenith:capsule", 1).rep(100, 10)));
    }

    // ---------------------------------------------------------------- the Kai of the north, at the end of Snake Way
    static {
        add(new Quest("catch_the_monkey", Quest.Giver.NORTH_KAI, List.of(Objective.kill(com.dbzenith.npc.TrainingMonkey.CAUGHT, 1)), List.of(), 0, false,
                Reward.tp(800).flag("skill:kaioken:1")));
        add(new Quest("strike_the_cricket", Quest.Giver.NORTH_KAI, List.of(Objective.kill(com.dbzenith.npc.TrainingCricket.STRUCK, 1)),
                List.of("catch_the_monkey"), 0, false, Reward.tp(1200).teach("gathering_sphere")));
        add(new Quest("kaioken_times_three", Quest.Giver.NORTH_KAI, List.of(Objective.level(400), Objective.kill("dbzenith:damned_warrior", 5)),
                List.of("strike_the_cricket"), 0, false, Reward.tp(2000).flag("skill:kaioken:3")));
    }

    // ---------------------------------------------------------------- the Grand Kai, in his paradise
    static {
        add(new Quest("springs_of_paradise", Quest.Giver.GRAND_KAI, List.of(Objective.flag("spring_soaked")), List.of(), 0, false,
                Reward.tp(1500).flag("godki:300")));
        add(new Quest("tournament_of_the_dead", Quest.Giver.GRAND_KAI, List.of(Objective.kill("dbzenith:damned_warrior", 12)),
                List.of("springs_of_paradise"), 0, false, Reward.tp(3000).with("dbzenith:senzu_bean", 6)));
    }

    private Quests() {}

    private static void add(Quest q) {
        BY_ID.put(q.id(), q);
    }

    public static Quest byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Quest> all() {
        return List.copyOf(BY_ID.values());
    }

    public static List<Quest> by(Quest.Giver giver) {
        return BY_ID.values().stream().filter(q -> q.giver() == giver).toList();
    }
}
