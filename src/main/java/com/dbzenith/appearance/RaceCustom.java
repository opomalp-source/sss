package com.dbzenith.appearance;

import com.dbzenith.race.Variant;
import com.dbzenith.stats.Race;

/**
 * What each race lets you change about its own look (CX-16b), so not every Frost Demon is the same white and purple
 * with the same horns, and not every Namekian the same green: a skin colour (races with a skin of their own), a
 * marking colour (Namekian bands, Frost Demon shell, Bio-Android spots), the style of the race's signature part
 * (classic, long, short or none) and that part's colour (horns, the tail). -1 always means "the race's own".
 */
public final class RaceCustom {
    /** The part a race's style choice shapes. */
    public enum Part { NONE, HORNS, ANTENNAE, TENTACLE, EARS, DEMON_HORNS, WINGS, TAIL, ALIEN }

    /** Size of the part for each style: classic, long, short, none. */
    public static final float[] LENGTH = {1f, 1.45f, 0.6f, 0f};

    public record Options(Part part, int[] skin, int[] marks, String marksKey, int[] partColors) {
        public boolean any() {
            return part != Part.NONE || skin.length > 0 || marks.length > 0 || partColors.length > 0;
        }
    }

    private static final int[] NONE = {};
    private static final int[] BONE = {0xEDE6D6, 0x2A2430, 0xE8C050, 0xC83030, 0x8A4AC8, 0xB8C4D0, 0x3A70D8};
    private static final int[] FUR = {0x6B3E1E, 0x1C1A1A, 0xC0283A, 0xE8B840, 0x8A8A90, 0xF0F0F0, 0x8A5A2A};

    private RaceCustom() {}

    public static Options of(Race race, Variant variant) {
        return switch (race) {
            case SAIYAN, HALF_SAIYAN -> new Options(Part.TAIL, NONE, NONE, "", FUR);
            case NAMEKIAN -> new Options(Part.ANTENNAE, new int[]{0x62B444, 0x4A9A5A, 0x8AC85A, 0x5AB0A0, 0x7A9A4A, 0xA8C870, 0x3A8A6A},
                    new int[]{0xE09A9A, 0xD86A8A, 0xE8B07A, 0xB88AD8, 0x6AB0E8, 0xE8E070}, "screen.dbzenith.custom_bands", NONE);
            case FROST_DEMON -> new Options(Part.HORNS, new int[]{0xF0EEF4, 0xE6D8F4, 0xD8E6F4, 0xF4D8E4, 0xB8BCC8, 0x3A3242, 0xF0E2B8},
                    new int[]{0x8A4AC8, 0x3A70D8, 0xD83A6A, 0x2AA870, 0xD8A030, 0xC83030, 0x202028, 0x60C8E8}, "screen.dbzenith.custom_shell", BONE);
            case MAJIN -> new Options(Part.TENTACLE, new int[]{0xF59AC0, 0xF0B8D8, 0x9AC0F0, 0xF0D890, 0xB8E8A0, 0xC8A0F0, 0xA8A8B0}, NONE, "", NONE);
            case VAMPIRE -> new Options(Part.EARS, new int[]{0xE6E0EA, 0xD8D0E8, 0xE8D8D0, 0xC8D0D8, 0xB0A0B8}, NONE, "", NONE);
            case BIO_ANDROID -> new Options(Part.WINGS, new int[]{0x5AB04A, 0x3A9070, 0x8AB040, 0x4A7AC0, 0xC0A040, 0x9A5AC0},
                    new int[]{0x1E3A1A, 0x3A1A3A, 0x1A2A4A, 0x4A2A10, 0x101010}, "screen.dbzenith.custom_spots", NONE);
            case TUFFLE -> new Options(Part.NONE, new int[]{0xEDE0D6, 0xF4E8DC, 0xE0CCC0, 0xD8D0C8, 0xC8B8A8}, NONE, "", NONE);
            case GEN_ALIEN -> new Options(Part.ALIEN, new int[]{0x7A9AC0, 0x9AC07A, 0xC07A9A, 0xC0A07A, 0x8A7AC0, 0x7AC0B8, 0xA0A0A8}, NONE, "", BONE);
            case CORE_PERSON -> variant == Variant.DEMON
                    ? new Options(Part.DEMON_HORNS, new int[]{0xC83030, 0x3A3AA8, 0x6A2A8A, 0x2A2A2A, 0x8A3020}, NONE, "", BONE)
                    : new Options(Part.EARS, new int[]{0xD8B8EC, 0xE8C0D8, 0xB8C8EC, 0xC8E8D0, 0xF0D8B8}, NONE, "", NONE);
            default -> new Options(Part.NONE, NONE, NONE, "", NONE);
        };
    }

    /** The style names for a part: what "long", "short" and "none" mean for it. */
    public static String styleKey(Part part, int style) {
        String[] names = part == Part.ALIEN ? new String[]{"none", "horns", "antennae", "ram"} : new String[]{"classic", "long", "short", "none"};
        return "screen.dbzenith.style." + names[Math.max(0, Math.min(3, style))];
    }

    /** How many styles a part has (a tail is only coloured; ears, wings and the rest have all four). */
    public static int styles(Part part) {
        return part == Part.NONE || part == Part.TAIL ? 0 : 4;
    }
}
