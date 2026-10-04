package com.dbzenith.stats;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;

/** Spending TP on attributes. Server-side only; the client just requests. */
public final class AttributeTraining {
    /** Upper bound per request so a bad packet can't loop for long. */
    public static final int MAX_STEPS_PER_REQUEST = 100;

    private AttributeTraining() {}

    /** Raises {@code attribute} up to {@code times} points while TP allows. Returns points actually gained. */
    public static int upgrade(PlayerData data, Attribute attribute, int times) {
        int cap = DBZConfig.SERVER.attributeHardCap.get();
        int steps = Math.min(Math.max(0, times), MAX_STEPS_PER_REQUEST);
        int gained = 0;
        for (int i = 0; i < steps; i++) {
            if (data.getAttribute(attribute) >= cap) break;
            long cost = StatCalculator.tpCost(data, attribute);
            if (data.getTrainingPoints() < cost) break;
            data.setTrainingPoints(data.getTrainingPoints() - cost);
            data.setAttribute(attribute, data.getAttribute(attribute) + 1);
            gained++;
        }
        if (gained > 0) data.recomputeIfStale();
        return gained;
    }
}
