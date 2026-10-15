package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.fx.BloodFx;
import com.dbzenith.combat.Scars;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Random;

/**
 * Battle scars on the body ({@link Scars}, the limbs rework): each scarred fighter gets their own texture, painted here
 * in the player skin layout at 128 pixels from their list of scars, in their own skin's tones (and their blood's, while
 * fresh), and drawn over the body like a skin layer (a jacket layer or a gi over it covers it).
 * <ul>
 *   <li><b>Cut:</b> a fine pale line, tapered at both ends, with a raised, darker lip either side, wandering a little;
 *       some long ones healed with stitches, and the stitch marks stayed.</li>
 *   <li><b>Split:</b> a short, jagged, wider seam, where a heavy blow broke the skin.</li>
 *   <li><b>Burn:</b> a mottled, shiny, irregular patch with a darker rim.</li>
 *   <li><b>Puncture:</b> a small round pucker with short lines drawn out from it.</li>
 * </ul>
 * Fresh, a scar is angry and red (toward the colour of the fighter's blood); over three days it settles to pale scar
 * tissue. The texture is only repainted when something changes.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public class ScarLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final int SIZE = 128, S = SIZE / 64;
    /** Days for a scar to settle from angry red to pale. */
    private static final float SETTLE_DAYS = 3f;
    /** Where each part's scars go (x, y, w, h in 64-pixel skin units): {front, outer side}. */
    private static final int[][][] FACES = {
            {{8, 8, 8, 8}, {8, 8, 8, 8}},                 // face
            {{20, 20, 8, 12}, {20, 20, 8, 12}},           // chest
            {{32, 20, 8, 12}, {32, 20, 8, 12}},           // back
            {{44, 20, 4, 12}, {40, 20, 4, 12}},           // right arm: front, outer side
            {{36, 52, 4, 12}, {40, 52, 4, 12}},           // left arm
            {{4, 20, 4, 12}, {0, 20, 4, 12}},             // right leg
            {{20, 52, 4, 12}, {24, 52, 4, 12}},           // left leg
    };

    private static final class Painted {
        List<Scars.Scar> scars;
        int key = Integer.MIN_VALUE;
        DynamicTexture texture;
        ResourceLocation id;
    }

    private static final Int2ObjectOpenHashMap<Painted> PAINTED = new Int2ObjectOpenHashMap<>();

    public ScarLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** From the server: this fighter's scars now. */
    public static void set(int entityId, List<Scars.Scar> scars) {
        Painted p = PAINTED.get(entityId);
        if (scars.isEmpty()) {
            if (p != null) release(entityId, p);
            return;
        }
        if (p == null) PAINTED.put(entityId, p = new Painted());
        p.scars = List.copyOf(scars);
        p.key = Integer.MIN_VALUE;
    }

    public static List<Scars.Scar> of(int entityId) {
        Painted p = PAINTED.get(entityId);
        return p == null ? List.of() : p.scars;
    }

    private static void release(int entityId, Painted p) {
        PAINTED.remove(entityId);
        if (p.id != null) Minecraft.getInstance().getTextureManager().release(p.id);
    }

    /** Lets go of the textures of fighters no longer around. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.level == null) {
            for (int id : PAINTED.keySet().toIntArray()) release(id, PAINTED.get(id));
            return;
        }
        if (mc.level.getGameTime() % 200 != 0) return;
        for (int id : PAINTED.keySet().toIntArray()) {
            Painted p = PAINTED.get(id);
            if (mc.level.getEntity(id) == null && p.id != null) {          // gone from sight: paint again when back
                mc.getTextureManager().release(p.id);
                p.id = null;
                p.texture = null;
                p.key = Integer.MIN_VALUE;
            }
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (e.isInvisible()) return;
        Painted p = PAINTED.get(e.getId());
        if (p == null || p.scars.isEmpty()) return;
        long now = e.level().getGameTime();
        int skin = Skin.of(e), blood = BloodFx.color(e);
        int key = skin * 31 + blood;
        for (Scars.Scar s : p.scars) key = key * 31 + (int) (fade(s, now) * 16);
        if (key != p.key || p.texture == null) {
            paint(e.getId(), p, skin, blood, now);
            p.key = key;
        }
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(p.id)), light,
                LivingEntityRenderer.getOverlayCoords(e, 0), 1f, 1f, 1f, 1f);
    }

    /** 0 fresh .. 1 settled. */
    private static float fade(Scars.Scar s, long now) {
        return Math.max(0, Math.min(1, (now - s.made()) / (24000f * SETTLE_DAYS)));
    }

    // ------------------------------------------------------------------ painting

    private static void paint(int entityId, Painted p, int skin, int blood, long now) {
        if (p.texture == null) {
            p.texture = new DynamicTexture(SIZE, SIZE, true);
            p.id = Minecraft.getInstance().getTextureManager().register(DBZenith.MOD_ID + "_scars_" + entityId, p.texture);
        }
        float[] px = new float[SIZE * SIZE * 4];                                // straight rgba, painted over
        int pale = mix(skin, 0xF4E8E0, 0.38f), lip = mix(skin, 0x2A1414, 0.32f);
        int angry = mix(skin, mix(0xC2463E, blood, 0.3f), 0.62f), angryLip = mix(angry, 0x2A0A0A, 0.3f);
        for (Scars.Scar s : p.scars) {
            float f = fade(s, now);
            int core = mix(angry, pale, f), edge = mix(angryLip, lip, f);
            int[] face = FACES[Math.max(0, Math.min(FACES.length - 1, s.part()))][s.side() == 0 ? 0 : 1];
            int x0 = face[0] * S, y0 = face[1] * S, w = face[2] * S, h = face[3] * S;
            float cx = x0 + s.u() * w, cy = y0 + s.v() * h;
            float reach = (float) (Math.hypot(w, h) * (s.part() == Scars.FACE ? 0.7 : 0.85));
            Random r = new Random(s.seed());
            switch (s.kind()) {
                case Scars.BURN -> burn(px, x0, y0, w, h, cx, cy, Math.max(1.8f, Math.min(Math.min(w, h) * 0.3f, reach * s.length() * 0.17f)), core, edge, r);
                case Scars.PUNCTURE -> puncture(px, x0, y0, w, h, cx, cy, core, edge, r);
                case Scars.SPLIT -> line(px, x0, y0, w, h, cx, cy, s.angle(), Math.max(3, reach * s.length() * 0.45f), 0.9f, 0.75f, core, edge, false, r);
                default -> line(px, x0, y0, w, h, cx, cy, s.angle(), Math.max(4, reach * s.length() * 0.5f), 0.6f, 0.18f, core, edge,
                        s.length() > 0.55f && (s.seed() & 3) == 0, r);
            }
        }
        NativeImage img = p.texture.getPixels();
        if (img == null) return;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int i = (y * SIZE + x) * 4;
            int a = Math.round(px[i + 3] * 255);
            if (a <= 0) {
                img.setPixelRGBA(x, y, 0);
                continue;
            }
            int rr = Math.round(px[i] * 255), gg = Math.round(px[i + 1] * 255), bb = Math.round(px[i + 2] * 255);
            img.setPixelRGBA(x, y, a << 24 | bb << 16 | gg << 8 | rr);         // (NativeImage keeps ABGR)
        }
        p.texture.upload();
        if (Boolean.getBoolean("dbzenith.devAutomation")) {                   // dev: the painted scars, to look at up close
            try {
                img.writeToFile(new java.io.File(Minecraft.getInstance().gameDirectory, "screenshots/dbz_scars_" + entityId + ".png"));
            } catch (java.io.IOException ignored) {
            }
        }
    }

    /**
     * A cut or a split: half-length {@code half} along {@code angle}, the core {@code width} wide at the middle; a split
     * zigzags ({@code jag}), a cut only wanders. Stitched: little pale marks across it every few pixels.
     */
    private static void line(float[] px, int x0, int y0, int w, int h, float cx, float cy, float angle, float half, float width, float jag,
                             int core, int edge, boolean stitched, Random r) {
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        float phase = r.nextFloat() * 6.28f, freq = 0.5f + r.nextFloat() * 0.4f, zig = 2.8f + r.nextFloat() * 1.6f;
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) {
            float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
            float t = dx * ca + dy * sa, d = -dx * sa + dy * ca;
            if (Math.abs(t) > half + 1) continue;
            float along = t / half;
            float taper = (float) Math.sqrt(Math.max(0, 1 - along * along));
            float off = jag > 0.5f ? (float) (jag * (Math.abs(((t / zig) % 2 + 2) % 2 - 1) * 2 - 1)) : (float) (Math.sin(t * freq + phase) * jag * 2);
            float dd = Math.abs(d - off), cw = width * (0.35f + 0.65f * taper);
            if (taper <= 0) continue;
            if (dd < cw) put(px, x, y, core, (0.55f + 0.4f * taper) * Math.min(1, (cw - dd) * 2 + 0.5f));
            else if (dd < cw + 0.9f) put(px, x, y, edge, 0.45f * taper * (1 - (dd - cw) / 0.9f));
        }
        if (stitched) {
            float step = 2.6f + r.nextFloat() * 0.6f;
            for (float t = -half + step * 0.7f; t < half - step * 0.5f; t += step) {
                for (int side = -1; side <= 1; side += 2) {
                    float sx = cx + ca * t - sa * side * 1.8f, sy = cy + sa * t + ca * side * 1.8f;
                    int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy);
                    if (ix >= x0 && ix < x0 + w && iy >= y0 && iy < y0 + h) put(px, ix, iy, core, 0.7f);
                }
            }
        }
    }

    /** A burn: a lobed patch, mottled and shiny, its rim darker. */
    private static void burn(float[] px, int x0, int y0, int w, int h, float cx, float cy, float rad, int core, int edge, Random r) {
        float[] lobe = {r.nextFloat() * 0.3f, r.nextFloat() * 0.2f, r.nextFloat() * 6.28f, r.nextFloat() * 6.28f};
        long seed = r.nextLong();
        int shine = mix(core, 0xFFFFFF, 0.25f), deep = mix(core, edge, 0.35f);
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) {
            float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
            double ang = Math.atan2(dy, dx);
            float edgeR = rad * (float) (1 + lobe[0] * Math.sin(ang * 3 + lobe[2]) + lobe[1] * Math.sin(ang * 5 + lobe[3]));
            float d = (float) Math.hypot(dx, dy * 0.9f);
            if (d > edgeR + 1) continue;
            if (d > edgeR) {
                put(px, x, y, edge, 0.4f * (edgeR + 1 - d));
                continue;
            }
            float n = (float) hash(x, y, seed) * 0.6f + (float) hash(x / 2, y / 2, seed + 1) * 0.4f;
            int c = n > 0.62f ? deep : n < 0.22f ? shine : core;
            if (dx < 0 && dy < 0 && d < edgeR * 0.5f && n > 0.4f && n < 0.55f) c = shine;   // a sheen, where it catches the light
            float rim = d > edgeR - 1 ? 0.7f : 0.85f;
            put(px, x, y, d > edgeR - 1 ? mix(c, edge, 0.4f) : c, rim);
        }
    }

    /** A puncture: a small round pucker, darker at the middle, with short lines drawn out from it. */
    private static void puncture(float[] px, int x0, int y0, int w, int h, float cx, float cy, int core, int edge, Random r) {
        int rays = 4 + r.nextInt(3);
        float start = r.nextFloat() * 6.28f;
        for (int k = 0; k < rays; k++) {
            float a = start + k * 6.28f / rays + (r.nextFloat() - 0.5f) * 0.5f, len = 2.2f + r.nextFloat() * 1.6f;
            for (float t = 1; t < len; t += 0.5f) {
                int ix = (int) Math.floor(cx + Math.cos(a) * t), iy = (int) Math.floor(cy + Math.sin(a) * t);
                if (ix >= x0 && ix < x0 + w && iy >= y0 && iy < y0 + h) put(px, ix, iy, core, 0.55f * (1 - t / len));
            }
        }
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) {
            float d = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy);
            if (d < 0.8f) put(px, x, y, edge, 0.75f);
            else if (d < 1.8f) put(px, x, y, core, 0.85f);
            else if (d < 2.5f) put(px, x, y, edge, 0.35f * (2.5f - d) / 0.7f);
        }
    }

    /** Paints {@code rgb} at {@code alpha} over what is there. */
    private static void put(float[] px, int x, int y, int rgb, float alpha) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE || alpha <= 0) return;
        alpha = Math.min(1, alpha);
        int i = (y * SIZE + x) * 4;
        float da = px[i + 3], oa = alpha + da * (1 - alpha);
        if (oa <= 0) return;
        float[] c = {(rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f};
        for (int k = 0; k < 3; k++) px[i + k] = (c[k] * alpha + px[i + k] * da * (1 - alpha)) / oa;
        px[i + 3] = oa;
    }

    static int mix(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static double hash(int x, int y, long seed) {
        long h = x * 374761393L + y * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }
}
