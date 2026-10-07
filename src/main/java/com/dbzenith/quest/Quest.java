package com.dbzenith.quest;

import java.util.List;

/**
 * A quest definition. Pure data; progress and rewards are handled by {@link QuestManager}.
 *
 * @param id          stable id (lang: quest.dbzenith.&lt;id&gt; and .desc)
 * @param giver       which NPC offers it
 * @param objectives  all must be met
 * @param requires    quest ids that must be completed first
 * @param minRank     Galactic Patrol rank needed (patrol quests)
 * @param repeatable  can be taken again after turning in (bounties)
 * @param reward      what turning it in gives
 */
public record Quest(String id, Giver giver, List<Objective> objectives, List<String> requires, int minRank,
                    boolean repeatable, Reward reward) {

    public enum Giver { MASTER, PATROL, NORTH_KAI, GRAND_KAI, BEERUS, WHIS }

    /** One goal. KILL counts events; the others are checked against the player's state. */
    public record Objective(Type type, String target, int amount) {
        public enum Type { KILL, REACH_LEVEL, LEARN_TECHNIQUE, ANY_FORM, COLLECT_ITEM, VISIT_DIMENSION, HAS_FLAG }

        public static Objective kill(String entityId, int count) { return new Objective(Type.KILL, entityId, count); }
        public static Objective level(int level) { return new Objective(Type.REACH_LEVEL, "", level); }
        public static Objective learn(String techniqueId) { return new Objective(Type.LEARN_TECHNIQUE, techniqueId, 1); }
        public static Objective anyForm() { return new Objective(Type.ANY_FORM, "", 1); }
        public static Objective collect(String itemId, int count) { return new Objective(Type.COLLECT_ITEM, itemId, count); }
        public static Objective visit(String dimensionId) { return new Objective(Type.VISIT_DIMENSION, dimensionId, 1); }
        public static Objective flag(String flag) { return new Objective(Type.HAS_FLAG, flag, 1); }
    }

    /**
     * @param tp        training points
     * @param items     item id x count pairs
     * @param flag      character flag to set ("" = none)
     * @param technique technique to teach ("" = none)
     * @param patrolRep Galactic Patrol reputation
     * @param alignment alignment change
     */
    public record Reward(long tp, List<ItemReward> items, String flag, String technique, int patrolRep, int alignment) {
        public static Reward tp(long tp) {
            return new Reward(tp, List.of(), "", "", 0, 0);
        }

        public Reward with(String itemId, int count) {
            List<ItemReward> list = new java.util.ArrayList<>(items);
            list.add(new ItemReward(itemId, count));
            return new Reward(tp, List.copyOf(list), flag, technique, patrolRep, alignment);
        }

        public Reward flag(String f) { return new Reward(tp, items, f, technique, patrolRep, alignment); }
        public Reward teach(String t) { return new Reward(tp, items, flag, t, patrolRep, alignment); }
        public Reward rep(int r, int align) { return new Reward(tp, items, flag, technique, r, align); }
    }

    public record ItemReward(String itemId, int count) {}

    public String titleKey() {
        return "quest.dbzenith." + id;
    }

    public String descKey() {
        return "quest.dbzenith." + id + ".desc";
    }
}
