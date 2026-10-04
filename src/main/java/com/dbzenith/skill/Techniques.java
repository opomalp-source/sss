package com.dbzenith.skill;

import com.dbzenith.stats.Race;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dbzenith.skill.Technique.Effect;
import static com.dbzenith.skill.Technique.Style;

/**
 * The technique library. Original designs inspired by classic archetypes. Generic techniques are learned with TP
 * (Techniques screen) or scrolls; racial ones are granted by the race.
 */
public final class Techniques {
    private static final Map<String, Technique> BY_ID = new LinkedHashMap<>();

    // ---------------------------------------------------------------- generic library
    public static final Technique KI_BLAST = add(Technique.builder("ki_blast").cost(20).damage(1.0).speed(1.6f).size(0.5f)
            .cooldown(10).color(0x7FD4FF).life(60));
    public static final Technique KI_SENSE = add(Technique.builder("ki_sense").style(Style.SELF).effect(Effect.KI_SENSE, 48)
            .cost(10).cooldown(200).color(0xFFFFFF).learn(100, 10));
    public static final Technique RAPID_VOLLEY = add(Technique.builder("rapid_volley").cost(40).damage(0.4).speed(1.8f).size(0.35f)
            .cooldown(30).volley(6, 7f).color(0xFFE070).life(50).learn(150, 20));
    public static final Technique HOMING_ORB = add(Technique.builder("homing_orb").cost(35).damage(0.8).speed(0.9f).size(0.6f)
            .cooldown(25).homing().color(0xC77DFF).life(100).learn(200, 40));
    public static final Technique INSTANT_STEP = add(Technique.builder("instant_step").style(Style.SELF).effect(Effect.TELEPORT, 10)
            .cost(40).cooldown(60).color(0xFFFFFF).learn(300, 50));
    public static final Technique CUTTER_DISK = add(Technique.builder("cutter_disk").cost(50).damage(1.5).speed(1.4f).size(0.9f)
            .cooldown(40).pierce(3).style(Style.DISK).color(0xFFF4A0).life(80).learn(300, 60));
    public static final Technique EXPLOSIVE_WAVE = add(Technique.builder("explosive_wave").style(Style.SELF).effect(Effect.EXPLOSIVE_WAVE, 5)
            .cost(70).damage(1.5).cooldown(100).color(0xFFFFFF).learn(350, 70));
    public static final Technique WAVE_BEAM = add(Technique.builder("wave_beam").style(Style.BEAM).cost(80).damage(3.0).speed(4f).size(0.9f)
            .cooldown(60).explosion(1.5f).color(0x4FA8FF).life(30).learn(400, 80));
    public static final Technique KI_HEAL = add(Technique.builder("ki_heal").style(Style.SELF).effect(Effect.HEAL_ALLY, 0.2)
            .cost(60).cooldown(200).color(0x9CFF9C).learn(400, 90));
    public static final Technique FINGER_BEAM = add(Technique.builder("finger_beam").style(Style.BEAM).cost(45).damage(2.0).speed(48f).size(0.18f)
            .cooldown(40).color(0xFF5FD2).life(8).learn(500, 120));

    // ---------------------------------------------------------------- racial
    public static final Technique SOLAR_FLARE = add(Technique.builder("solar_flare").style(Style.SELF).effect(Effect.BLIND_AREA, 12)
            .cost(30).cooldown(300).color(0xFFFFE0).race(Race.HUMAN));
    public static final Technique CRIMSON_BEAM = add(Technique.builder("crimson_beam").style(Style.BEAM).cost(90).damage(3.4).speed(4f).size(1.0f)
            .cooldown(70).explosion(1.5f).color(0xB04CFF).life(30).race(Race.SAIYAN));
    public static final Technique TWIN_WAVE = add(Technique.builder("twin_wave").style(Style.BEAM).cost(85).damage(3.2).speed(4f).size(1.1f)
            .cooldown(70).explosion(1.5f).color(0x6FD0FF).life(30).race(Race.HALF_SAIYAN));
    public static final Technique REGENERATE = add(Technique.builder("regenerate").style(Style.SELF).effect(Effect.HEAL_SELF, 0.35)
            .cost(80).cooldown(300).color(0x8CFF7A).race(Race.NAMEKIAN));
    public static final Technique SUPERNOVA_ORB = add(Technique.builder("supernova_orb").cost(150).damage(4.0).speed(0.7f).size(2.5f)
            .cooldown(200).explosion(3.0f).color(0xFF7A30).life(120).race(Race.FROST_DEMON));
    public static final Technique CANDY_BEAM = add(Technique.builder("candy_beam").effect(Effect.CANDY, 40).cost(40).damage(0.2)
            .speed(2.0f).size(0.4f).cooldown(100).color(0xFF80C0).life(40).race(Race.MAJIN));
    public static final Technique ENERGY_ABSORB = add(Technique.builder("energy_absorb").style(Style.SELF).effect(Effect.ENERGY_ABSORB, 60)
            .cost(0).cooldown(300).color(0x9AD0FF).race(Race.ANDROID));
    public static final Technique ARM_CANNON = add(Technique.builder("arm_cannon").cost(30).damage(1.8).speed(2.6f).size(0.45f)
            .cooldown(15).color(0xFFB050).life(40).race(Race.CYBORG));

    private Techniques() {}

    private static Technique add(Technique.Builder b) {
        Technique t = b.build();
        BY_ID.put(t.id(), t);
        return t;
    }

    public static Technique byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Technique> all() {
        return List.copyOf(BY_ID.values());
    }

    /** Techniques a race can ever learn, generic first. */
    public static List<Technique> forRace(Race race) {
        return BY_ID.values().stream().filter(t -> t.races().contains(race)).toList();
    }
}
