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
        BALD, SPIKY, WILD, SWEPT, SLICK, BOWL, MOHAWK, PONYTAIL, LONG, TOPKNOT;

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

    public static List<Strand> strands(Preset p) {
        List<Strand> s = new ArrayList<>();
        switch (p) {
            case BALD -> { }
            case SPIKY -> {
                for (int u = 1; u <= 6; u += 2) for (int v = 1; v <= 6; v += 2)
                    s.add(new Strand(Face.TOP, u, v, (u - 3) * 1, -(v - 3), 6 + (v < 3 ? 1 : 0), 3, Bend.STRAIGHT));
                for (int u = 1; u <= 6; u += 2) s.add(new Strand(Face.FRONT, u, 0, (u - 3), -1, 4, 2, Bend.DROOP));
                for (int v = 1; v <= 4; v += 3) {
                    s.add(new Strand(Face.LEFT, 4, v, 1, 2, 5, 3, Bend.STRAIGHT));
                    s.add(new Strand(Face.RIGHT, 4, v, -1, 2, 5, 3, Bend.STRAIGHT));
                }
                for (int u = 1; u <= 6; u += 2) s.add(new Strand(Face.BACK, u, 2, 0, -1, 5, 3, Bend.DROOP));
            }
            case WILD -> {
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 7; v += 2)
                    s.add(new Strand(Face.TOP, u, v, (u - 3) * 2, -(v - 4) * 2, 7, 3, Bend.STRAIGHT));
                for (int v = 1; v <= 5; v += 2) {
                    s.add(new Strand(Face.LEFT, 5, v, 2, 3, 6, 3, Bend.STRAIGHT));
                    s.add(new Strand(Face.RIGHT, 5, v, -2, 3, 6, 3, Bend.STRAIGHT));
                }
                for (int u = 0; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 2, (u - 3), 0, 6, 3, Bend.STRAIGHT));
                s.add(new Strand(Face.FRONT, 3, 0, 0, -2, 5, 2, Bend.DROOP));
            }
            case SWEPT -> {
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 6; v += 2)
                    s.add(new Strand(Face.TOP, u, v, 0, -5, 6, 3, Bend.DROOP));
                for (int u = 0; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 1, 0, -6, 5, 3, Bend.DROOP));
                s.add(new Strand(Face.LEFT, 3, 1, 0, -3, 3, 2, Bend.STRAIGHT));
                s.add(new Strand(Face.RIGHT, 3, 1, 0, -3, 3, 2, Bend.STRAIGHT));
            }
            case SLICK -> {
                for (int u = 1; u <= 6; u += 2) for (int v = 0; v <= 6; v += 3)
                    s.add(new Strand(Face.TOP, u, v, 0, -5, 4, 3, Bend.STRAIGHT));
                for (int u = 1; u <= 6; u += 2) s.add(new Strand(Face.BACK, u, 1, 0, -6, 3, 3, Bend.DROOP));
                s.add(new Strand(Face.FRONT, 2, 0, 1, -6, 4, 1, Bend.HANG));                    // one loose bang
            }
            case BOWL -> {
                for (int u = 0; u <= 7; u += 2) for (int v = 0; v <= 6; v += 3) s.add(new Strand(Face.TOP, u, v, 0, 0, 2, 4, Bend.STRAIGHT));
                for (int u = 0; u <= 7; u += 2) s.add(new Strand(Face.FRONT, u, 0, 0, -6, 3, 3, Bend.HANG));
                for (int u = 0; u <= 7; u += 2) {
                    s.add(new Strand(Face.LEFT, u, 0, 0, -6, 4, 3, Bend.HANG));
                    s.add(new Strand(Face.RIGHT, u, 0, 0, -6, 4, 3, Bend.HANG));
                    s.add(new Strand(Face.BACK, u, 0, 0, -6, 5, 3, Bend.HANG));
                }
            }
            case MOHAWK -> {
                for (int v = 0; v <= 7; v++) s.add(new Strand(Face.TOP, 3 + v % 2, v, 0, -(v - 4), 7 - Math.abs(v - 3), 2, Bend.STRAIGHT));
                s.add(new Strand(Face.BACK, 3, 1, 0, -2, 5, 2, Bend.DROOP));
            }
            case PONYTAIL -> {
                for (int u = 1; u <= 6; u += 2) for (int v = 0; v <= 6; v += 3) s.add(new Strand(Face.TOP, u, v, 0, -5, 4, 3, Bend.STRAIGHT));
                s.add(new Strand(Face.BACK, 3, 1, 0, -2, 16, 3, Bend.HANG));
                s.add(new Strand(Face.BACK, 4, 1, 0, -2, 15, 3, Bend.HANG));
                s.add(new Strand(Face.FRONT, 1, 0, -2, -5, 4, 1, Bend.HANG));
                s.add(new Strand(Face.FRONT, 6, 0, 2, -5, 4, 1, Bend.HANG));
            }
            case LONG -> {
                for (int u = 1; u <= 6; u += 2) for (int v = 0; v <= 6; v += 3) s.add(new Strand(Face.TOP, u, v, 0, -4, 4, 3, Bend.DROOP));
                for (int u = 0; u <= 7; u += 2) s.add(new Strand(Face.BACK, u, 0, 0, -6, 14, 3, Bend.HANG));
                s.add(new Strand(Face.LEFT, 6, 0, 0, -6, 10, 3, Bend.HANG));
                s.add(new Strand(Face.RIGHT, 6, 0, 0, -6, 10, 3, Bend.HANG));
                s.add(new Strand(Face.FRONT, 2, 0, -1, -6, 5, 2, Bend.HANG));
                s.add(new Strand(Face.FRONT, 5, 0, 1, -6, 5, 2, Bend.HANG));
            }
            case TOPKNOT -> {
                for (int u = 1; u <= 6; u += 2) for (int v = 1; v <= 6; v += 2) s.add(new Strand(Face.TOP, u, v, 0, 0, 1, 4, Bend.STRAIGHT));
                s.add(new Strand(Face.TOP, 3, 5, 0, -2, 5, 4, Bend.STRAIGHT));
                s.add(new Strand(Face.TOP, 4, 5, 0, -2, 5, 4, Bend.STRAIGHT));
            }
        }
        return s;
    }

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
        for (Strand s : base) {
            int pitch = s.face == Face.TOP ? s.pitch / 2 : Math.max(s.pitch, 0) + 2;   // top: towards vertical; sides: lifted
            int yaw = s.face == Face.TOP ? s.yaw * 2 / 3 : s.yaw;
            int len = Math.round(Math.max(s.length, 4) * lengthScale) + extra;
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
