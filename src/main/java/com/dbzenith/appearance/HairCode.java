package com.dbzenith.appearance;

import com.dbzenith.transform.Form;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
    private static final Map<String, Preset> LEGACY_PRESETS = loadLegacyPresets();

    private static Map<String, Preset> loadLegacyPresets() {
        Properties properties = new Properties();
        try (var input = HairCode.class.getResourceAsStream("/assets/dbzenith/hair/legacy-presets.properties")) {
            if (input != null) properties.load(input);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load hair preset migrations", e);
        }
        Map<String, Preset> out = new java.util.HashMap<>();
        for (String code : properties.stringPropertyNames()) out.put(code, Preset.valueOf(properties.getProperty(code)));
        return Map.copyOf(out);
    }

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
        // Only exact shipped presets are upgraded. Hand-edited and shared custom codes retain their own strands.
        Preset legacy = LEGACY_PRESETS.get(code);
        if (legacy != null) return strands(legacy);
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
        SIDECUT, GOHAN_ADULT;

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

    /** Authored clumps, in head pixels. Keep the existing code format and preset IDs for saved characters. */
    private static void lock(List<Strand> s, Face f, int u, int v, int yaw, int pitch, int length, int width, Bend bend) {
        s.add(new Strand(f, u, v, yaw, pitch, length, width, bend));
    }

    private static void pair(List<Strand> s, Face f, int u, int v, int yaw, int pitch, int len, int width, Bend bend) {
        lock(s, f, u, v, yaw, pitch, len, width, bend);
        switch (f) {
            case LEFT -> lock(s, Face.RIGHT, 7 - u, v, -yaw, pitch, len, width, bend);
            case RIGHT -> lock(s, Face.LEFT, 7 - u, v, -yaw, pitch, len, width, bend);
            default -> lock(s, f, 7 - u, v, -yaw, pitch, len, width, bend);
        }
    }

    /** A fitted crown, rather than a grid of competing spikes. */
    private static void crown(List<Strand> s, int pitch) {
        pair(s, Face.TOP, 1, 3, -2, pitch, 2, 3, Bend.STRAIGHT);
        pair(s, Face.TOP, 2, 6, -1, -2, 2, 3, Bend.STRAIGHT);
        pair(s, Face.LEFT, 2, 0, 0, -3, 2, 2, Bend.DROOP);
    }

    private static void nape(List<Strand> s, int length) {
        pair(s, Face.BACK, 1, 2, -2, -3, length, 3, Bend.DROOP);
        pair(s, Face.BACK, 2, 4, -1, -4, Math.max(3, length - 1), 2, Bend.DROOP);
    }

    public static List<Strand> strands(Preset p) {
        List<Strand> s = new ArrayList<>();
        switch (p) {
            case BALD -> { }
            case SPIKY -> { // Goku: asymmetric fan, three hanging bangs, layered outward nape.
                crown(s, 0);
                lock(s, Face.TOP, 1, 3, -4, 0, 10, 4, Bend.STRAIGHT);
                lock(s, Face.TOP, 2, 4, -1, -1, 11, 4, Bend.STRAIGHT);
                lock(s, Face.TOP, 4, 3, 2, 0, 8, 4, Bend.STRAIGHT);
                lock(s, Face.TOP, 4, 5, 1, -1, 12, 2, Bend.STRAIGHT);
                lock(s, Face.TOP, 6, 4, 4, -1, 9, 4, Bend.STRAIGHT);
                lock(s, Face.TOP, 5, 6, 2, -3, 8, 3, Bend.STRAIGHT);
                lock(s, Face.TOP, 2, 6, -4, -3, 8, 3, Bend.STRAIGHT);
                lock(s, Face.LEFT, 4, 1, 2, 1, 6, 3, Bend.STRAIGHT);
                lock(s, Face.RIGHT, 3, 1, -2, 1, 7, 3, Bend.STRAIGHT);
                lock(s, Face.FRONT, 1, 0, -1, -5, 5, 3, Bend.HANG);
                lock(s, Face.FRONT, 4, 0, 1, -5, 5, 3, Bend.HANG);
                lock(s, Face.FRONT, 6, 0, 2, -4, 3, 2, Bend.DROOP);
                nape(s, 6);
            }
            case PRINCE -> { // Vegeta: narrow flame, swept sides and a central widow's peak.
                crown(s, -2);
                pair(s, Face.TOP, 3, 2, 0, -1, 12, 3, Bend.STRAIGHT);
                pair(s, Face.TOP, 2, 4, -1, -2, 11, 3, Bend.STRAIGHT);
                pair(s, Face.TOP, 1, 2, -2, -1, 8, 3, Bend.LIFT);
                pair(s, Face.TOP, 1, 6, -2, -3, 8, 3, Bend.LIFT);
                lock(s, Face.TOP, 4, 6, 0, -2, 10, 3, Bend.STRAIGHT);
                lock(s, Face.FRONT, 3, 0, 0, -5, 3, 2, Bend.STRAIGHT);
                pair(s, Face.LEFT, 2, 0, 1, 4, 5, 2, Bend.LIFT);
                pair(s, Face.LEFT, 5, 1, 2, 4, 5, 2, Bend.LIFT);
                pair(s, Face.BACK, 2, 1, 0, 4, 6, 3, Bend.LIFT);
            }
            case TEEN, GOHAN_ADULT -> { // Stable editable codes identify each separately sculpted Gohan preset.
                crown(s, -2);
                lock(s, Face.TOP, 2, 2, -2, -1, 7, 4, Bend.STRAIGHT);
                lock(s, Face.TOP, 4, 3, 0, -2, 8, 4, Bend.STRAIGHT);
                lock(s, Face.TOP, 6, 3, 2, -2, 6, 3, Bend.STRAIGHT);
                pair(s, Face.TOP, 1, 6, -3, -3, 6, 3, Bend.STRAIGHT);
                lock(s, Face.FRONT, 2, 0, 1, -5, p == Preset.TEEN ? 6 : 4, 3, Bend.HANG);
                lock(s, Face.FRONT, 5, 0, 2, -3, 2, 2, Bend.DROOP);
                pair(s, Face.LEFT, 4, 1, 2, 2, 4, 2, Bend.STRAIGHT);
                nape(s, 4);
            }
            case WILD -> { // Broly: heavy spreading locks, with a lower, rougher outer silhouette.
                crown(s, 0);
                pair(s, Face.TOP, 1, 2, -3, 1, 9, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 2, 5, -2, -2, 10, 4, Bend.STRAIGHT);
                lock(s, Face.TOP, 4, 3, 1, 0, 11, 4, Bend.STRAIGHT);
                pair(s, Face.LEFT, 4, 1, 2, 1, 8, 4, Bend.DROOP);
                pair(s, Face.LEFT, 6, 3, 2, -2, 7, 3, Bend.DROOP);
                pair(s, Face.FRONT, 1, 0, -2, -4, 6, 3, Bend.HANG);
                lock(s, Face.FRONT, 4, 0, 1, -4, 4, 2, Bend.DROOP);
                nape(s, 8);
            }
            case MANE -> { // Raditz: crown spikes flow into separate long back tiers.
                crown(s, -1);
                pair(s, Face.TOP, 1, 2, -3, 0, 8, 4, Bend.STRAIGHT);
                pair(s, Face.TOP, 3, 4, -1, -2, 9, 4, Bend.STRAIGHT);
                pair(s, Face.BACK, 1, 0, -2, -4, 16, 4, Bend.HANG);
                pair(s, Face.BACK, 3, 1, 0, -5, 16, 4, Bend.HANG);
                pair(s, Face.BACK, 2, 4, -1, -4, 12, 3, Bend.HANG);
                pair(s, Face.LEFT, 5, 0, 1, -4, 12, 3, Bend.HANG);
                pair(s, Face.FRONT, 1, 0, -2, -4, 4, 2, Bend.DROOP);
            }
            case SAGE, LONG -> {
                crown(s, -3);
                pair(s, Face.TOP, 2, 1, -3, -1, 3, 3, Bend.DROOP);
                pair(s, Face.FRONT, 1, 0, -2, -5, p == Preset.SAGE ? 7 : 5, 2, Bend.HANG);
                pair(s, Face.LEFT, 5, 0, 0, -5, p == Preset.SAGE ? 14 : 11, 3, Bend.HANG);
                pair(s, Face.BACK, 1, 0, 0, -6, 14, 3, Bend.HANG);
                pair(s, Face.BACK, 3, 0, 0, -6, 16, 3, Bend.HANG);
                if (p == Preset.SAGE) pair(s, Face.BACK, 2, 3, -1, -5, 12, 3, Bend.HANG);
            }
            case CURTAINS -> { // Trunks: centre part with broad cheek-framing locks.
                crown(s, -3);
                pair(s, Face.TOP, 2, 1, -3, 1, 3, 3, Bend.DROOP);
                pair(s, Face.FRONT, 1, 0, -2, -5, 6, 3, Bend.HANG);
                pair(s, Face.FRONT, 2, 0, -2, -3, 4, 2, Bend.HANG);
                pair(s, Face.LEFT, 4, 0, 0, -5, 5, 3, Bend.HANG);
                pair(s, Face.BACK, 1, 0, 0, -6, 6, 3, Bend.HANG);
                pair(s, Face.BACK, 3, 1, 0, -6, 5, 3, Bend.HANG);
            }
            case SWEPT, SLICK -> {
                crown(s, -3);
                pair(s, Face.TOP, 1, 1, -1, -4, p == Preset.SWEPT ? 7 : 5, 3, Bend.DROOP);
                pair(s, Face.TOP, 3, 2, 0, -4, p == Preset.SWEPT ? 8 : 5, 3, Bend.DROOP);
                pair(s, Face.TOP, 2, 5, -1, -4, 5, 3, Bend.DROOP);
                pair(s, Face.LEFT, 4, 0, 1, 1, 3, 2, Bend.STRAIGHT);
                nape(s, p == Preset.SWEPT ? 5 : 3);
                if (p == Preset.SLICK) lock(s, Face.FRONT, 2, 0, 1, -5, 4, 1, Bend.HANG);
            }
            case BOWL -> {
                crown(s, 0);
                pair(s, Face.TOP, 1, 1, 0, 0, 2, 3, Bend.STRAIGHT);
                pair(s, Face.FRONT, 0, 0, 0, -6, 3, 2, Bend.STRAIGHT);
                pair(s, Face.FRONT, 2, 0, 0, -6, 3, 2, Bend.STRAIGHT);
                pair(s, Face.LEFT, 2, 0, 0, -6, 4, 3, Bend.HANG);
                pair(s, Face.LEFT, 5, 0, 0, -6, 4, 3, Bend.HANG);
                pair(s, Face.BACK, 1, 0, 0, -6, 5, 3, Bend.HANG);
                pair(s, Face.BACK, 3, 0, 0, -6, 5, 3, Bend.HANG);
            }
            case BUZZ -> {
                for (int u = 1; u <= 6; u += 2) for (int v = 1; v <= 6; v += 2)
                    lock(s, Face.TOP, u, v, 0, 0, 1, 2, Bend.STRAIGHT);
                pair(s, Face.BACK, 1, 0, 0, -4, 1, 3, Bend.STRAIGHT);
            }
            case PUFF -> {
                crown(s, 0);
                pair(s, Face.TOP, 1, 1, -2, 2, 4, 4, Bend.LIFT);
                pair(s, Face.TOP, 3, 2, 0, 0, 5, 4, Bend.LIFT);
                pair(s, Face.TOP, 1, 5, -2, -2, 4, 4, Bend.LIFT);
                pair(s, Face.LEFT, 2, 0, -1, 1, 3, 4, Bend.LIFT);
                pair(s, Face.LEFT, 5, 1, 1, 0, 3, 4, Bend.LIFT);
                pair(s, Face.BACK, 1, 1, -1, 1, 4, 4, Bend.LIFT);
                pair(s, Face.BACK, 3, 1, 0, 0, 4, 4, Bend.LIFT);
            }
            case MOHAWK -> {
                for (int v = 1; v <= 6; v++) lock(s, Face.TOP, 3, v, 0, 2 - v / 2, 7 - Math.abs(v - 3), 2, Bend.STRAIGHT);
                lock(s, Face.BACK, 3, 0, 0, 2, 4, 2, Bend.STRAIGHT);
            }
            case PONYTAIL, TWINTAILS -> {
                crown(s, -3);
                pair(s, Face.TOP, 2, 1, -1, -4, 4, 3, Bend.DROOP);
                pair(s, Face.FRONT, 1, 0, -1, -5, 4, 2, Bend.HANG);
                if (p == Preset.PONYTAIL) {
                    pair(s, Face.BACK, 3, 1, 0, -1, 15, 3, Bend.HANG);
                    lock(s, Face.BACK, 4, 2, 1, -2, 12, 2, Bend.HANG);
                } else {
                    pair(s, Face.LEFT, 6, 0, 1, -1, 13, 3, Bend.HANG);
                    pair(s, Face.LEFT, 6, 1, 2, -2, 11, 2, Bend.HANG);
                }
            }
            case TOPKNOT -> {
                crown(s, -3);
                pair(s, Face.TOP, 2, 1, 1, -4, 3, 3, Bend.DROOP);
                pair(s, Face.TOP, 3, 5, 0, -1, 4, 3, Bend.DROOP);
                lock(s, Face.TOP, 4, 6, 0, 3, 3, 3, Bend.HANG);
                nape(s, 3);
            }
            case SIDECUT -> {
                lock(s, Face.TOP, 1, 2, 3, -1, 6, 3, Bend.DROOP);
                lock(s, Face.TOP, 3, 2, 4, -1, 7, 4, Bend.DROOP);
                lock(s, Face.TOP, 5, 4, 4, -2, 6, 3, Bend.DROOP);
                lock(s, Face.LEFT, 2, 0, 0, -5, 7, 3, Bend.HANG);
                lock(s, Face.LEFT, 5, 0, 1, -4, 6, 3, Bend.HANG);
                pair(s, Face.BACK, 2, 1, 0, -4, 3, 3, Bend.DROOP);
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
