package com.dbzenith.data;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.stats.Attribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Named numeric fields of {@link PlayerData}, addressable by string id (used by /dbz set and later by quests/rewards).
 */
public record StatField(String id, ToDoubleFunction<PlayerData> getter, BiConsumer<PlayerData, Double> setter) {
    private static final Map<String, StatField> BY_ID = new LinkedHashMap<>();

    static {
        for (Attribute a : Attribute.values()) {
            register(a.id(), d -> d.getAttribute(a),
                    (d, v) -> d.setAttribute(a, (int) Math.min(DBZConfig.SERVER.attributeHardCap.get(), Math.round(v))));
        }
        register("tp", d -> d.getTrainingPoints(), (d, v) -> d.setTrainingPoints(Math.round(v)));
        register("body", PlayerData::getBody, PlayerData::setBody);
        register("ki", PlayerData::getKi, PlayerData::setKi);
        register("stamina", PlayerData::getStamina, PlayerData::setStamina);
        register("release", PlayerData::getReleasePercent, (d, v) -> d.setReleasePercent((int) Math.round(v)));
        register("alignment", PlayerData::getAlignment, (d, v) -> d.setAlignment((int) Math.round(v)));
        register("physical_age", PlayerData::getPhysicalAge, PlayerData::setPhysicalAge);
        register("mental_age", PlayerData::getMentalAge, PlayerData::setMentalAge);
        register("prestige", d -> d.getPrestige(), (d, v) -> d.setPrestige((int) Math.round(v)));
        register("thirst", PlayerData::getThirst, PlayerData::setThirst);
        register("scar", d -> d.getScar(), (d, v) -> d.setCosmetics((int) Math.max(0, Math.min(com.dbzenith.world.Cosmetics.SCARS.size() - 1, Math.round(v))), d.getTattoo()));
        register("tattoo", d -> d.getTattoo(), (d, v) -> d.setCosmetics(d.getScar(), (int) Math.max(0, Math.min(com.dbzenith.world.Cosmetics.TATTOOS.size() - 1, Math.round(v)))));
    }

    private static void register(String id, ToDoubleFunction<PlayerData> getter, BiConsumer<PlayerData, Double> setter) {
        BY_ID.put(id, new StatField(id, getter, setter));
    }

    public static StatField byId(String id) {
        return BY_ID.get(id);
    }

    public static List<String> ids() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.keySet()));
    }

    public double get(PlayerData data) {
        return getter.applyAsDouble(data);
    }

    public void set(PlayerData data, double value) {
        setter.accept(data, value);
    }
}
