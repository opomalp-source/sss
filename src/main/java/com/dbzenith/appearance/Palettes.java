package com.dbzenith.appearance;

/** The colour choices offered by the creation, barber and Life screens. Any RGB is accepted from the network. */
public final class Palettes {
    private Palettes() {}

    public static final int[] HAIR = {0x1C1A1A, 0x3A2A22, 0x5A3820, 0x8A5A30, 0xE8C860, 0xB03020, 0xEEEEEE, 0x8890A0,
            0x3050C0, 0x3C8C3C, 0xE070A0, 0x7040A0};

    /** -1 = the skin's own eyes. */
    public static final int[] EYES = {-1, 0x101010, 0x5A3820, 0x3070E0, 0x30A040, 0xD02020, 0xE0B020, 0x9040E0};

    /** -1 = keep the player's own Minecraft skin; the rest choose the generated body in that tone. */
    public static final int[] SKIN = {-1, 0xFFE2C6, 0xF4D0AA, 0xE8B98C, 0xD6A070, 0xBF8656, 0xA06A40, 0x7E5030, 0x5C3A22, 0x3E2818,
            0x8ED07A, 0xF0B8D0, 0xD8D4E8, 0xA8C8E8};
}
