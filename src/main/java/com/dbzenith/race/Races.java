package com.dbzenith.race;

import com.dbzenith.stats.Race;

import java.util.EnumMap;
import java.util.Map;

import static com.dbzenith.stats.Attribute.CONSTITUTION;
import static com.dbzenith.stats.Attribute.DEXTERITY;
import static com.dbzenith.stats.Attribute.KI_POWER;
import static com.dbzenith.stats.Attribute.MIND;
import static com.dbzenith.stats.Attribute.SPIRIT;
import static com.dbzenith.stats.Attribute.STRENGTH;
import static com.dbzenith.stats.Attribute.WILLPOWER;

/** Race definitions. Cost weights: below 1 = that attribute is cheaper to train for the race. */
public final class Races {
    private static final Map<Race, RaceTraits> TRAITS = new EnumMap<>(Race.class);

    static {
        add(RaceTraits.builder(Race.HUMAN).costs(1.0, 0.95, 1.0, 1.0, 1.0, 0.8, 0.9)
                .bonus(MIND, 3).bonus(SPIRIT, 2).aura(0xF2F6FF)
                .tpGain(0.15).kiCostReduction(0.15)
                .racial("solar_flare"));

        add(RaceTraits.builder(Race.SAIYAN).costs(0.85, 1.0, 0.95, 0.9, 1.0, 1.2, 1.1)
                .bonus(STRENGTH, 3).bonus(KI_POWER, 2).aura(0xFFF0C8)
                .zenkai(0.25).tail()
                .racial("crimson_beam"));

        add(RaceTraits.builder(Race.HALF_SAIYAN).costs(0.95, 0.95, 1.0, 0.9, 0.95, 0.95, 1.0)
                .bonus(KI_POWER, 2).bonus(MIND, 2).bonus(STRENGTH, 1).aura(0xFFF6DC)
                .zenkai(0.15).tpGain(0.05).tail()
                .racial("twin_wave"));

        add(RaceTraits.builder(Race.NAMEKIAN).costs(1.1, 1.0, 0.9, 0.9, 0.9, 0.9, 0.85)
                .bonus(SPIRIT, 3).bonus(WILLPOWER, 2).aura(0xB8FFB0)
                .regen(3.0, 0.25).feature(RaceTraits.Feature.ANTENNAE)
                .racial("regenerate"));

        add(RaceTraits.builder(Race.FROST_DEMON).costs(1.0, 0.85, 1.0, 0.85, 1.0, 1.1, 1.15)
                .bonus(DEXTERITY, 2).bonus(KI_POWER, 3).aura(0xE6C8FF)
                .breathless().feature(RaceTraits.Feature.HORNS)
                .racial("supernova_orb"));

        add(RaceTraits.builder(Race.MAJIN).costs(1.0, 1.0, 0.8, 1.0, 0.9, 1.15, 0.9)
                .bonus(CONSTITUTION, 3).bonus(WILLPOWER, 2).aura(0xFFB0D8)
                .regen(5.0, 0.1).killHeal(0.15).feature(RaceTraits.Feature.TENTACLE)
                .racial("candy_beam"));

        add(RaceTraits.builder(Race.ANDROID).costs(1.0, 1.0, 1.0, 1.0, 0.85, 1.2, 1.3)
                .bonus(STRENGTH, 2).bonus(DEXTERITY, 2).bonus(CONSTITUTION, 1).aura(0xC8E6FF)
                .noHunger().staminaless().kiAbsorb(0.3).kiRegen(2.0)
                .racial("energy_absorb"));

        add(RaceTraits.builder(Race.CYBORG).costs(0.9, 1.0, 0.9, 1.05, 1.0, 1.0, 1.1)
                .bonus(STRENGTH, 2).bonus(CONSTITUTION, 2).aura(0xD0D8E0)
                .slowHunger().kiAbsorb(0.15).staminaRegen(2.0)
                .racial("arm_cannon"));

        // ---- content expansion races
        add(RaceTraits.builder(Race.VAMPIRE).costs(0.95, 0.85, 1.05, 0.95, 1.0, 1.0, 1.05)
                .bonus(DEXTERITY, 3).bonus(STRENGTH, 2).aura(0xC01030)
                .regen(1.6, 0.6).feature(RaceTraits.Feature.EARS)
                .racial("blood_drain"));

        add(RaceTraits.builder(Race.BIO_ANDROID).costs(0.95, 1.0, 0.9, 0.95, 1.0, 1.05, 0.95)
                .bonus(CONSTITUTION, 2).bonus(KI_POWER, 2).bonus(STRENGTH, 1).aura(0x9AFF70)
                .regen(2.5, 0.4).kiAbsorb(0.1).noHunger().breathless().feature(RaceTraits.Feature.WINGS)
                .racial("perfect_barrier"));

        add(RaceTraits.builder(Race.TUFFLE).costs(1.05, 1.0, 1.0, 0.9, 0.95, 0.75, 1.0)
                .bonus(MIND, 4).bonus(KI_POWER, 1).aura(0xD0FF90)
                .tpGain(0.1).kiCostReduction(0.1)
                .racial("tuffle_cannon"));

        add(RaceTraits.builder(Race.GEN_ALIEN).costs(0.95, 0.95, 0.95, 0.95, 0.95, 1.0, 1.0)
                .bonus(STRENGTH, 1).bonus(DEXTERITY, 1).bonus(CONSTITUTION, 1).bonus(KI_POWER, 1).aura(0x8AE0FF)
                .feature(RaceTraits.Feature.ANTENNAE)
                .racial("chaos_barrage"));

        add(RaceTraits.builder(Race.CORE_PERSON).costs(1.05, 1.0, 1.0, 0.9, 0.85, 0.9, 0.85)
                .bonus(SPIRIT, 3).bonus(WILLPOWER, 2).aura(0xFFF2C8)
                .kiRegen(1.3).feature(RaceTraits.Feature.EARS)
                .racial("divine_restore", "dark_seal"));
    }

    private Races() {}

    private static void add(RaceTraits.Builder b) {
        RaceTraits t = b.build();
        TRAITS.put(t.race(), t);
    }

    public static RaceTraits of(Race race) {
        return TRAITS.get(race);
    }
}
