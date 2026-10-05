package com.dbzenith.appearance;

/**
 * A face, packed into one int (four bits a part): eyes, brows, mouth, nose, ears and an extra (marks, paint,
 * whiskers...). 0 everywhere is the plain face the generated bodies always had. Drawn by
 * {@code client.render.FaceLayer} from the overlays ArtGen paints ({@code textures/entity/face/<part>_<n>.png}).
 */
public final class FaceParts {
    public enum Part {
        EYES(8), BROWS(6), MOUTH(6), NOSE(4), EARS(2), EXTRA(8);

        public final int options;

        Part(int options) {
            this.options = options;
        }

        public String key() {
            return name().toLowerCase();
        }

        public String optionKey(int i) {
            return "face.dbzenith." + key() + "." + i;
        }
    }

    /** Eye shapes; the irises sit where each shape puts them (none for closed eyes). */
    public static final int EYES_NORMAL = 0, EYES_WIDE = 1, EYES_NARROW = 2, EYES_SHARP = 3, EYES_GENTLE = 4, EYES_TIRED = 5,
            EYES_CLOSED = 6, EYES_CAT = 7;
    public static final int EARS_ROUND = 0, EARS_POINTED = 1;

    private FaceParts() {}

    public static int get(int face, Part p) {
        int v = face >>> (p.ordinal() * 4) & 15;
        return v < p.options ? v : 0;
    }

    public static int with(int face, Part p, int value) {
        int v = Math.floorMod(value, p.options);
        return face & ~(15 << p.ordinal() * 4) | v << p.ordinal() * 4;
    }

    /** A face with every part in range (anything else from the wire becomes the default for that part). */
    public static int sanitize(int face) {
        int out = 0;
        for (Part p : Part.values()) out = with(out, p, get(face, p));
        return out;
    }
}
