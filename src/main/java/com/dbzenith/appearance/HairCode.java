package com.dbzenith.appearance;

import com.dbzenith.transform.Form;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Hair as data: a list of strands, each a tapered spike rooted on one face of the head on an 8x8 grid (the head's
 * pixels), pointed by yaw and pitch in 15 degree steps, with a length, a root width and a curve. A hair packs into
 * four bytes a strand and travels as a short shareable code ("DBZH1-" + base64), so players can trade hairstyles.
 * <p>
 * Head space is the vanilla head cube: x and z -4..4, y -8..0 (y down), the face looking towards -z. LEFT is the
 * wearer's left (+x).
 */
public final class HairCode {
    public static final String PREFIX = "DBZH1-";
    public static final int MAX_STRANDS = 64;
    public static final int MAX_CODE_LENGTH = PREFIX.length() + (MAX_STRANDS * 4 * 4 + 2) / 3 + 2;

    public enum Face { TOP, FRONT, BACK, LEFT, RIGHT }

    /** How a strand curves along its length. */
    public enum Bend { STRAIGHT, DROOP, HANG, LIFT }

    /**
     * @param yaw   -6..6, 15 degree steps across the face (towards +u)
     * @param pitch -6..6, 15 degree steps along the face (towards -v: up on the sides, forward on top)
     * @param length 1..16 pixels
     * @param width  1..4 pixels at the root
     */
    public record Strand(Face face, int u, int v, int yaw, int pitch, int length, int width, Bend bend) {
        public Strand {
            u = clamp(u, 0, 7);
            v = clamp(v, 0, 7);
            yaw = clamp(yaw, -6, 6);
            pitch = clamp(pitch, -6, 6);
            length = clamp(length, 1, 16);
            width = clamp(width, 1, 4);
        }

        int pack() {
            return face.ordinal() | u << 3 | v << 6 | (yaw + 6) << 9 | (pitch + 6) << 13 | (length - 1) << 17
                    | (width - 1) << 21 | bend.ordinal() << 23;
        }

        static Strand unpack(int p) {
            int f = p & 7;
            if (f >= Face.values().length) return null;
            return new Strand(Face.values()[f], p >> 3 & 7, p >> 6 & 7, (p >> 9 & 15) - 6, (p >> 13 & 15) - 6,
                    (p >> 17 & 15) + 1, (p >> 21 & 3) + 1, Bend.values()[p >> 23 & 3]);
        }

        public Strand with(int yaw, int pitch, int length, int width, Bend bend) {
            return new Strand(face, u, v, yaw, pitch, length, width, bend);
        }
    }

    private HairCode() {}

    // ------------------------------------------------------------------ codes

    public static String encode(List<Strand> strands) {
        if (strands.isEmpty()) return "";
        int n = Math.min(MAX_STRANDS, strands.size());
        byte[] bytes = new byte[n * 4];
        for (int i = 0; i < n; i++) {
            int p = strands.get(i).pack();
            bytes[i * 4] = (byte) p;
            bytes[i * 4 + 1] = (byte) (p >> 8);
            bytes[i * 4 + 2] = (byte) (p >> 16);
            bytes[i * 4 + 3] = (byte) (p >> 24);
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The strands of a code; empty for "" (bald), null for anything malformed. */
    public static List<Strand> decode(String code) {
        if (code == null || code.isEmpty()) return List.of();
        code = code.trim();
        if (!code.startsWith(PREFIX) || code.length() > MAX_CODE_LENGTH) return null;
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (bytes.length % 4 != 0 || bytes.length / 4 > MAX_STRANDS) return null;
        List<Strand> out = new ArrayList<>(bytes.length / 4);
        for (int i = 0; i < bytes.length; i += 4) {
            int p = (bytes[i] & 255) | (bytes[i + 1] & 255) << 8 | (bytes[i + 2] & 255) << 16 | (bytes[i + 3] & 255) << 24;
            Strand s = Strand.unpack(p);
            if (s == null) return null;
            out.add(s);
        }
        return out;
    }

    /** A code made canonical (re-encoded), or null if it is not valid. */
    public static String sanitize(String code) {
        List<Strand> s = decode(code);
        return s == null ? null : encode(s);
    }

    // ------------------------------------------------------------------ presets

    public enum Preset {
        BALD, SPIKY, PRINCE, WILD, TEEN, MANE, SAGE, CURTAINS, SWEPT, SLICK, BOWL, BUZZ, PUFF, MOHAWK, PONYTAIL, TWINTAILS, LONG, TOPKNOT,
        SIDECUT;

        public String code() {
            return encode(strands(this));
        }

        public String translationKey() {
            return "hair.dbzenith." + name().toLowerCase();
        }
    }

    /** The old fixed hairstyles (saved worlds) map onto presets. */
    public static String fromLegacyStyle(int hairStyleOrdinal) {
        Form.HairStyle[] styles = Form.HairStyle.values();
        if (hairStyleOrdinal <= 0 || hairStyleOrdinal >= styles.length) return "";
        return forStyle(styles[hairStyleOrdinal]);
    }

    /** A form's own hair when the character has none of their own to raise. */
    public static String forStyle(Form.HairStyle style) {
        return switch (style) {
            case NONE -> "";
            case SPIKY -> Preset.SPIKY.code();
            case SPIKY_TALL -> encode(raise(strands(Preset.SPIKY), 1.35f, 2));
            case LONG -> encode(lengthen(strands(Preset.SPIKY)));
            case SLIM -> Preset.SLICK.code();
        };
    }

    /** Short thick strands over the crown, sides and back so no scalp shows between the shaped strands. */
    private static void volume(List<Strand> s, int len, Bend topBend, int topPitch) {
        for (int u = 0; u <= 7; u += 2) for (int v = 1; v <= 7; v += 2) s.add(new Strand(Face.TOP, u, v, 0, topPitch, len, 4, topBend));
        for (int u = 1; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 1, 0, -4, len + 1, 4, Bend.DROOP));
        for (int u = 1; u <= 6; u += 3) {
            s.add(new Strand(Face.LEFT, u, 0, 1, -3, len, 3, Bend.DROOP));
            s.add(new Strand(Face.RIGHT, 7 - u, 0, -1, -3, len, 3, Bend.DROOP));
        }
    }

    /** A strand and its mirror on the other side of the head (left/right swap; top, face, back flip). */
    private static void pair(List<Strand> s, Face f, int u, int v, int yaw, int pitch, int len, int width, Bend bend) {
        s.add(new Strand(f, u, v, yaw, pitch, len, width, bend));
        switch (f) {
            case LEFT -> s.add(new Strand(Face.RIGHT, 7 - u, v, -yaw, pitch, len, width, bend));
            case RIGHT -> s.add(new Strand(Face.LEFT, 7 - u, v, -yaw, pitch, len, width, bend));
            default -> s.add(new Strand(f, 7 - u, v, -yaw, pitch, len, width, bend));
        }
    }

    public static List<Strand> strands(Preset p) {
        List<Strand> s = new ArrayList<>();
        switch (p) {
            case BALD -> { }
            case SPIKY -> {                                        // the classic hero: a few big spikes, up and out
                volume(s, 2, Bend.STRAIGHT, 0);
                pair(s, Face.TOP, 1, 2, -3, 0, 9, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 3, 1, -1, 1, 8, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 2, 5, -2, -2, 10, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 0, 4, -4, -1, 9, 4, Bend.STRAIGHT);
                pair(s, Face.LEFT, 3, 2, 3, 1, 6, 3, Bend.STRAIGHT);
                pair(s, Face.BACK, 2, 2, 1, -2, 8, 4, Bend.STRAIGHT);
                pair(s, Face.BACK, 3, 4, 0, -3, 7, 4, Bend.STRAIGHT);
                pair(s, Face.FRONT, 1, 0, -2, -2, 4, 2, Bend.DROOP);   // bangs framing the brow
                s.add(new Strand(Face.FRONT, 4, 0, 1, -1, 5, 2, Bend.DROOP));
            }
            case PRINCE -> {                                       // a tall flame swept up and back from a widow's peak
                volume(s, 3, Bend.LIFT, 0);
                pair(s, Face.TOP, 3, 1, 0, -1, 13, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 1, 1, -1, -1, 10, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 3, 4, 0, -2, 14, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 1, 4, -2, -2, 11, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 2, 6, -1, -3, 11, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 0, 2, -3, -1, 8, 4, Bend.STRAIGHT);
                pair(s, Face.FRONT, 3, 0, 0, 5, 6, 3, Bend.LIFT);         // the peak
                pair(s, Face.LEFT, 1, 1, 1, 5, 6, 3, Bend.LIFT);
                pair(s, Face.LEFT, 4, 1, 2, 4, 7, 3, Bend.LIFT);
                pair(s, Face.BACK, 2, 1, 0, 4, 8, 4, Bend.LIFT);
            }
            case WILD -> {                                         // untamed, everything everywhere
                volume(s, 3, Bend.STRAIGHT, 0);
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 7; v += 2)
                    s.add(new Strand(Face.TOP, u, v, (u - 3) * 2, -(v - 3), 8 + (u + v) % 3, 4, Bend.STRAIGHT));
                for (int v = 1; v <= 5; v += 2) pair(s, Face.LEFT, 5, v, 3, 2, 8, 3, Bend.STRAIGHT);
                for (int u = 0; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 2, (u - 3), -1, 9, 4, Bend.STRAIGHT));
                pair(s, Face.FRONT, 2, 0, -2, -2, 6, 2, Bend.DROOP);
            }
            case TEEN -> {                                         // medium spikes and one long lock over the eye
                volume(s, 2, Bend.STRAIGHT, 0);
                pair(s, Face.TOP, 1, 2, -3, 1, 6, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 3, 4, -1, -1, 7, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 1, 6, -3, -3, 6, 4, Bend.STRAIGHT);
                s.add(new Strand(Face.FRONT, 2, 0, -1, -5, 8, 2, Bend.HANG));
                pair(s, Face.LEFT, 3, 1, 2, 1, 5, 3, Bend.STRAIGHT);
                pair(s, Face.BACK, 2, 2, 0, -2, 7, 4, Bend.DROOP);
            }
            case MANE -> {                                         // a long spiked mane down the back
                volume(s, 3, Bend.STRAIGHT, -1);
                pair(s, Face.TOP, 1, 2, -3, 1, 8, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 3, 4, -1, -2, 9, 4, Bend.STRAIGHT);
                for (int u = 0; u <= 7; u += 2) {
                    s.add(new Strand(Face.BACK, u, 1, (u - 3) / 2, -5, 15, 4, Bend.DROOP));
                    s.add(new Strand(Face.BACK, u + 1 > 7 ? 7 : u + 1, 4, (u - 3) / 2, -5, 13, 3, Bend.DROOP));
                }
                pair(s, Face.LEFT, 5, 1, 2, -3, 11, 3, Bend.DROOP);
                pair(s, Face.FRONT, 2, 0, -2, -3, 6, 3, Bend.DROOP);
            }
            case SAGE -> {                                         // long and straight with a centre part
                for (int u = 0; u <= 7; u += 2) for (int v = 1; v <= 7; v += 2)
                    s.add(new Strand(Face.TOP, u, v, u < 4 ? -2 : 2, -5, 4, 4, Bend.HANG));
                for (int u = 0; u <= 7; u++) s.add(new Strand(Face.BACK, u, 0, 0, -6, 16, 4, Bend.HANG));
                for (int u = 1; u <= 7; u += 2) pair(s, Face.LEFT, u, 0, 0, -6, 13, 3, Bend.HANG);
                pair(s, Face.FRONT, 0, 0, -2, -5, 6, 2, Bend.HANG);
            }
            case CURTAINS -> {                                     // parted bangs falling either side of the face
                volume(s, 2, Bend.DROOP, -2);
                pair(s, Face.FRONT, 1, 0, -2, -4, 7, 3, Bend.HANG);
                pair(s, Face.FRONT, 2, 0, -1, -5, 7, 3, Bend.HANG);
                for (int u = 1; u <= 6; u += 2) pair(s, Face.LEFT, u, 0, 0, -6, 6, 3, Bend.HANG);
                for (int u = 0; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 0, 0, -6, 7, 4, Bend.HANG));
            }
            case SWEPT -> {
                volume(s, 2, Bend.DROOP, -4);
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 6; v += 2) s.add(new Strand(Face.TOP, u, v, 0, -5, 7, 4, Bend.DROOP));
                for (int u = 0; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 1, 0, -6, 6, 4, Bend.DROOP));
                pair(s, Face.LEFT, 3, 1, 1, -3, 4, 3, Bend.STRAIGHT);
            }
            case SLICK -> {
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 7; v += 2) s.add(new Strand(Face.TOP, u, v, 0, -5, 5, 4, Bend.DROOP));
                for (int u = 1; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 0, 0, -6, 4, 4, Bend.DROOP));
                pair(s, Face.LEFT, 2, 0, 1, -4, 3, 3, Bend.DROOP);
                s.add(new Strand(Face.FRONT, 2, 0, 1, -6, 5, 1, Bend.HANG));                    // one loose bang
            }
            case BOWL -> {
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 7; v += 2) s.add(new Strand(Face.TOP, u, v, 0, 0, 2, 4, Bend.STRAIGHT));
                for (int u = 0; u <= 7; u++) s.add(new Strand(Face.FRONT, u, 0, 0, -6, 3, 3, Bend.HANG));
                for (int u = 0; u <= 7; u += 2) {
                    pair(s, Face.LEFT, u, 0, 0, -6, 5, 3, Bend.HANG);
                    s.add(new Strand(Face.BACK, u, 0, 0, -6, 5, 4, Bend.HANG));
                }
            }
            case BUZZ -> {                                         // close-cropped: a short even layer of volume
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 7; v += 2) s.add(new Strand(Face.TOP, u, v, 0, -1, 1, 4, Bend.STRAIGHT));
                for (int u = 1; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 1, 0, -5, 1, 4, Bend.STRAIGHT));
                pair(s, Face.LEFT, 2, 0, 0, -4, 1, 4, Bend.STRAIGHT);
            }
            case PUFF -> {                                         // a round, springy cloud of curls
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 7; v += 2)
                    s.add(new Strand(Face.TOP, u, v, (u - 3), -(v - 3), 4, 4, Bend.LIFT));
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 2; v += 2) {
                    pair(s, Face.LEFT, u, v, 0, 1, 3, 4, Bend.LIFT);
                    s.add(new Strand(Face.BACK, u, v, 0, 1, 4, 4, Bend.LIFT));
                }
            }
            case MOHAWK -> {
                for (int v = 0; v <= 7; v++) s.add(new Strand(Face.TOP, 3 + v % 2, v, 0, -(v - 4), 8 - Math.abs(v - 3), 3, Bend.STRAIGHT));
                s.add(new Strand(Face.FRONT, 3, 0, 0, 3, 5, 3, Bend.STRAIGHT));
                s.add(new Strand(Face.BACK, 3, 1, 0, -2, 6, 3, Bend.DROOP));
            }
            case PONYTAIL -> {
                volume(s, 2, Bend.DROOP, -4);
                s.add(new Strand(Face.BACK, 3, 1, 0, -2, 16, 3, Bend.HANG));
                s.add(new Strand(Face.BACK, 4, 1, 0, -2, 15, 3, Bend.HANG));
                s.add(new Strand(Face.BACK, 3, 2, 0, -1, 14, 2, Bend.HANG));
                pair(s, Face.FRONT, 1, 0, -2, -5, 4, 1, Bend.HANG);
            }
            case TWINTAILS -> {
                volume(s, 2, Bend.DROOP, -3);
                pair(s, Face.LEFT, 6, 1, 4, -1, 12, 3, Bend.HANG);
                pair(s, Face.LEFT, 6, 2, 4, -2, 11, 3, Bend.HANG);
                pair(s, Face.FRONT, 2, 0, -1, -5, 5, 2, Bend.HANG);
            }
            case LONG -> {
                volume(s, 2, Bend.DROOP, -4);
                for (int u = 0; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 0, 0, -6, 14, 4, Bend.HANG));
                pair(s, Face.LEFT, 6, 0, 0, -6, 11, 3, Bend.HANG);
                pair(s, Face.FRONT, 2, 0, -1, -6, 5, 2, Bend.HANG);
            }
            case TOPKNOT -> {
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 7; v += 2) s.add(new Strand(Face.TOP, u, v, 0, 0, 1, 4, Bend.STRAIGHT));
                s.add(new Strand(Face.TOP, 3, 5, 0, -2, 5, 4, Bend.STRAIGHT));
                s.add(new Strand(Face.TOP, 4, 5, 0, -2, 5, 4, Bend.STRAIGHT));
                s.add(new Strand(Face.TOP, 3, 6, 0, -3, 4, 4, Bend.STRAIGHT));
            }
            case SIDECUT -> {                                      // shaved on one side, a long sweep over the other
                for (int u = 2; u <= 7; u += 2) for (int v = 0; v <= 7; v += 2) s.add(new Strand(Face.TOP, u, v, 3, -1, 7, 4, Bend.DROOP));
                s.add(new Strand(Face.LEFT, 2, 0, 1, -5, 9, 4, Bend.HANG));
                s.add(new Strand(Face.LEFT, 5, 0, 2, -5, 8, 4, Bend.HANG));
                for (int u = 1; u <= 7; u += 3) s.add(new Strand(Face.BACK, u, 1, 0, -5, 4, 4, Bend.DROOP));
            }
        }
        return s;
    }

    /** Whether a hair covers the skull (strands rooted across the top, both sides of the middle): a mohawk does not. */
    public static boolean hasVolume(String code) {
        return VOLUME.computeIfAbsent(code, c -> {
            List<Strand> s = decode(c);
            if (s == null) return false;
            boolean left = false, right = false;
            for (Strand st : s) {
                if (st.face() != Face.TOP && st.face() != Face.BACK) continue;
                if (st.u() <= 2) left = true;
                if (st.u() >= 5) right = true;
            }
            return left && right;
        });
    }

    private static final java.util.Map<String, Boolean> VOLUME = new java.util.concurrent.ConcurrentHashMap<>();

    // ------------------------------------------------------------------ transformations

    /**
     * The hair a form gives you, grown from your own: Super Saiyan-style forms lift and stiffen every strand, taller
     * forms lengthen them further, the long-haired form lets them fall to the waist, slim god forms tame them.
     * A form without hair of its own leaves the hair alone (only its colour may change). Bald characters get the
     * form's stock hair.
     */
    public static String forForm(String baseCode, Form form) {
        Form.HairStyle style = form.hairStyle();
        if (style == Form.HairStyle.NONE) return baseCode;
        List<Strand> base = decode(baseCode);
        if (base == null || base.isEmpty()) return forStyle(style);
        return encode(switch (style) {
            case SPIKY -> raise(base, 1.2f, 1);
            case SPIKY_TALL -> raise(base, 1.45f, 2);
            case LONG -> lengthen(raise(base, 1.1f, 0));
            case SLIM -> tame(base);
            case NONE -> base;
        });
    }

    /** Stiffen: no droop, strands swing towards straight up and out, longer. */
    static List<Strand> raise(List<Strand> base, float lengthScale, int extra) {
        List<Strand> out = new ArrayList<>(base.size());
        boolean cropped = base.stream().allMatch(s -> s.length <= 3);                  // a buzz cut still flares into spikes
        for (Strand s : base) {
            int pitch = s.face == Face.TOP ? s.pitch / 2 : Math.max(s.pitch, 0) + 2;   // top: towards vertical; sides: lifted
            int yaw = s.face == Face.TOP ? s.yaw * 2 / 3 : s.yaw;
            int len = !cropped && s.length <= 3 ? s.length + 1 + extra / 2              // the volume layer stays underneath
                    : Math.round(Math.max(s.length, 4) * lengthScale) + extra;
            out.add(s.with(yaw, pitch, len, Math.max(2, s.width), Bend.STRAIGHT));
        }
        return out;
    }

    /** A mane: back and side strands fall long; the top keeps its lift. */
    static List<Strand> lengthen(List<Strand> base) {
        List<Strand> out = new ArrayList<>(base);
        for (int u = 0; u <= 7; u += 2) out.add(new Strand(Face.BACK, u, 1, (u - 3) / 2, -6, 16, 4, Bend.HANG));
        out.add(new Strand(Face.LEFT, 6, 1, 0, -6, 14, 3, Bend.HANG));
        out.add(new Strand(Face.RIGHT, 6, 1, 0, -6, 14, 3, Bend.HANG));
        return out.size() > MAX_STRANDS ? out.subList(0, MAX_STRANDS) : out;
    }

    /** Smoothed down and a little shorter. */
    static List<Strand> tame(List<Strand> base) {
        List<Strand> out = new ArrayList<>(base.size());
        for (Strand s : base) out.add(s.with(s.yaw / 2, s.face == Face.TOP ? s.pitch - 2 : s.pitch - 1, Math.max(2, s.length * 4 / 5), s.width, Bend.DROOP));
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
