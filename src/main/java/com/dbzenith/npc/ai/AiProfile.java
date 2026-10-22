package com.dbzenith.npc.ai;

import com.dbzenith.npc.KiFighter;
import com.dbzenith.race.Variant;
import com.dbzenith.stats.Race;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What kind of fighter an NPC type is, for its AI: its side, the temperaments it is born with (one is picked from its
 * UUID), the race and variant whose existing form line it may climb (null: it has no forms), whether it can fly, how
 * well it thinks, whether it is important enough to remember its opponents, and its own aura colour.
 */
public record AiProfile(Faction faction, List<Personality> personalities, Race race, Variant variant, boolean canFly,
                        Tier tier, boolean important, int aura) {
    /** How hard it thinks. Capped by the server's {@code npc_ai.quality}. */
    public enum Tier { LIGHT, NORMAL, ELITE }

    public static final int PLAIN_AURA = 0xD9F2FF;
    private static final AiProfile DEFAULT = new AiProfile(Faction.LONER, List.of(Personality.BALANCED), null, null, false, Tier.NORMAL, false, PLAIN_AURA);
    private static final Map<String, AiProfile> BY_TYPE = new HashMap<>();

    static {
        BY_TYPE.put("sproutling", new AiProfile(Faction.FRIEZA_FORCE, List.of(Personality.RECKLESS, Personality.AGGRESSIVE), null, null, false,
                Tier.LIGHT, false, 0x9BE870));
        BY_TYPE.put("ki_soldier", new AiProfile(Faction.FRIEZA_FORCE, List.of(Personality.AGGRESSIVE, Personality.BALANCED, Personality.ARROGANT,
                Personality.COWARDLY), null, null, true, Tier.NORMAL, false, PLAIN_AURA));
        BY_TYPE.put("android_unit", new AiProfile(Faction.RED_RIBBON, List.of(Personality.TACTICAL, Personality.PATIENT), Race.ANDROID, Variant.ANDROID,
                true, Tier.NORMAL, false, 0x9AD0FF));
        BY_TYPE.put("namekian_warrior", new AiProfile(Faction.NAMEKIANS, List.of(Personality.HONORABLE, Personality.DEFENSIVE, Personality.PATIENT),
                Race.NAMEKIAN, Variant.WARRIOR_CLAN, true, Tier.NORMAL, false, 0xB8FFB0));
        BY_TYPE.put("ogre_guard", new AiProfile(Faction.OTHERWORLD, List.of(Personality.DEFENSIVE, Personality.HONORABLE), null, null, false,
                Tier.LIGHT, false, PLAIN_AURA));
        BY_TYPE.put("damned_warrior", new AiProfile(Faction.DAMNED, List.of(Personality.AGGRESSIVE, Personality.RECKLESS, Personality.ARROGANT), null, null,
                false, Tier.NORMAL, false, 0xC04050));
        BY_TYPE.put("tyrant_lord", new AiProfile(Faction.FRIEZA_FORCE, List.of(Personality.ARROGANT), Race.FROST_DEMON, Variant.FROST_DEMON, true,
                Tier.ELITE, true, 0xC890FF));
        BY_TYPE.put("rampage_brute", new AiProfile(Faction.LONER, List.of(Personality.RECKLESS), Race.SAIYAN, Variant.LEGENDARY, true,
                Tier.ELITE, true, 0x7CFF4A));
        // the World Martial Arts Tournament: grounded martial artists (no flying in the ring), each their own temperament
        tournament("mr_satan", Personality.ARROGANT);
        tournament("spopovich", Personality.RECKLESS);
        tournament("pintar", Personality.ARROGANT);
        tournament("jewel", Personality.ARROGANT);
        tournament("nam", Personality.HONORABLE);
        tournament("ranfan", Personality.TACTICAL);
        tournament("yamu", Personality.AGGRESSIVE);
    }

    private static void tournament(String id, Personality p) {
        BY_TYPE.put(id, new AiProfile(Faction.TOURNAMENT, List.of(p), null, null, false, Tier.NORMAL, false, PLAIN_AURA));
    }

    public static AiProfile of(KiFighter k) {
        return of(k.getType());
    }

    public static AiProfile of(EntityType<?> type) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return key == null ? DEFAULT : BY_TYPE.getOrDefault(key.getPath(), DEFAULT);
    }

    /** The temperament an NPC is born with: one of its type's, picked by its UUID (stable, saved afterwards). */
    public Personality born(long seed) {
        return personalities.get(AiRandom.pick(seed, 0, 7, personalities.size()));
    }
}
