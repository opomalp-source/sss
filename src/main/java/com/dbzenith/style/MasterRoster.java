package com.dbzenith.style;

import java.util.Locale;

/**
 * The masters who teach fighting styles (CX-20): each its own NPC type ({@code dbzenith:<id>}) with its own look, a
 * spawn egg, and data in {@code masters/<id>.json} (the style they move in, what they like) and the styles that name
 * them. Size: render scale and hitbox; colours: the spawn egg's.
 */
public enum MasterRoster {
    GOKU(1f, 1.85f, 0xF07818, 0x1E3C8C),
    VEGETA(0.95f, 1.8f, 0x2850A8, 0xF0F0F0),
    MASTER_ROSHI(0.86f, 1.6f, 0xF0A030, 0x3C7A3C),
    KRILLIN(0.82f, 1.55f, 0xF07818, 0xF0C8A0),
    PICCOLO(1.08f, 2.05f, 0x5AA040, 0xF0F0F0),
    TIEN(1.04f, 1.95f, 0x2E8A4A, 0xE8D8B8),
    GOHAN(1f, 1.85f, 0x6A2AA0, 0x2848A0),
    FUTURE_TRUNKS(1f, 1.85f, 0x3A5AA0, 0xC8B8E8),
    FRIEZA(0.9f, 1.7f, 0xF0F0F8, 0x8A30C0),
    CELL(1.06f, 2.0f, 0x7ABA48, 0x1A1A1A),
    ANDROID_17(1f, 1.85f, 0x2A6A4A, 0x101010),
    HIT(1.08f, 2.05f, 0x6A2A8A, 0x283850),
    JIREN(1.12f, 2.1f, 0xC8D0D8, 0xC82020),
    BROLY(1.2f, 2.25f, 0x3AB848, 0xE8C8A0),
    YAMCHA(1f, 1.85f, 0xF07818, 0x283C8C),
    MAJIN_BUU(1.08f, 1.95f, 0xF0A0C8, 0x6A2A8A);

    public final float scale, height;
    public final int eggBase, eggSpots;

    MasterRoster(float scale, float height, int eggBase, int eggSpots) {
        this.scale = scale;
        this.height = height;
        this.eggBase = eggBase;
        this.eggSpots = eggSpots;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MasterRoster byId(String id) {
        for (MasterRoster m : values()) if (m.id().equals(id)) return m;
        return null;
    }
}
