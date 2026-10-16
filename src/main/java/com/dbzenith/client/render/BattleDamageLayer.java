package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.fx.BloodFx;
import com.dbzenith.config.DBZConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Battle damage (CX-47): a fighter shows the fight on them. (The knocked-out rework: the rips now come from how torn the
 * clothes are, {@link #torn}, and stay until they are mended; only the bruises and grime still follow health.) Below 70% health the clothes start to rip (skin showing
 * through), with frayed edges, scratches and grime; below 45% and 20% more of it, and bruises. Heavy blows and hard
 * landings cover them in dust, which wears off over a minute (at once in water).
 * <p>The limbs rework: it follows the fighter's race. The rips show their own skin ({@link Skin}), cuts are in their
 * own blood's colour, bruises are made from their skin, and each fighter has one of three layouts, so it doesn't look
 * the same on everyone. Textures from {@code tools/BattleDamageGen.java}; client config {@code battleDamage}.
 */
public class BattleDamageLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final String[] LAYERS = {"tears", "edges", "cuts", "bruises"};
    /** [hi-res][layout][layer * 3 + level - 1], layers as {@link #LAYERS}. */
    private static final ResourceLocation[][][] TEX = {textures("battle"), textures("battle_hd")};
    private static final ResourceLocation[] DUST_TEX = {dust("battle"), dust("battle_hd")};
    private static final int TEARS = 0, EDGES = 3, CUTS = 6, BRUISES = 9;

    private static ResourceLocation[][] textures(String folder) {
        ResourceLocation[][] t = new ResourceLocation[3][12];
        for (int v = 0; v < 3; v++) for (int l = 0; l < LAYERS.length; l++) for (int i = 1; i <= 3; i++)
            t[v][l * 3 + i - 1] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/" + folder + "/v" + v + "/" + LAYERS[l] + "_" + i + ".png");
        return t;
    }

    private static ResourceLocation dust(String folder) {
        return new ResourceLocation(DBZenith.MOD_ID, "textures/entity/" + folder + "/dust.png");
    }

    /** Each fighter's dust, and what it was last time we looked. */
    private static final class Dust {
        float amount;
        float lastAge = Float.NaN;
        boolean wasHurt, wasAirborne;
        float fall;
    }

    private static final Map<LivingEntity, Dust> DUST = new WeakHashMap<>();

    public BattleDamageLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** How torn each fighter's own clothes are, as last heard (the knocked-out rework). */
    private static final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap CLOTHES = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

    public static void clothes(int entityId, int level) {
        if (level <= 0) CLOTHES.remove(entityId);
        else CLOTHES.put(entityId, level);
    }

    /**
     * How torn the clothes are, 0 whole .. 3 in tatters (the knocked-out rework: it stays until mended). The most worn
     * gi piece worn, or the fighter's own clothes; anything else (a mob with no record of it) by health as before.
     */
    public static int torn(LivingEntity e) {
        int gi = com.dbzenith.combat.Clothes.shown(e);
        if (gi >= 0) return gi;
        if (com.dbzenith.combat.Clothes.eligible(e)) return CLOTHES.getOrDefault(e.getId(), 0);
        return level(e);
    }

    /** 0 unhurt .. 3 battered, from health: the bruises, scratches and grime. */
    public static int level(LivingEntity e) {
        float max = e.getMaxHealth();
        if (max <= 0) return 0;
        float r = e.getHealth() / max;
        return r < 0.2f ? 3 : r < 0.45f ? 2 : r < 0.7f ? 1 : 0;
    }

    /** Which of the three layouts a fighter's damage takes: always the same one for the same fighter. */
    public static int layout(LivingEntity e) {
        return Math.floorMod(e.getUUID().hashCode() * 31 + 7, 3);
    }

    /** A bruise on this skin: darker and toward plum, so a dark skin bruises darker still and a green one goes olive-purple. */
    public static int bruise(int skin) {
        int r = skin >> 16 & 255, g = skin >> 8 & 255, b = skin & 255;
        return (int) (r * 0.45 + 0x4A * 0.55) << 16 | (int) (g * 0.4 + 0x22 * 0.6) << 8 | (int) (b * 0.45 + 0x5A * 0.55);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (e.isInvisible() || !DBZConfig.CLIENT.battleDamage.get() || ApeLook.is(e)) return;
        int hd = ArtStyle.hiRes() ? 1 : 0;
        ResourceLocation[] tex = TEX[hd][layout(e)];
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0);
        int torn = torn(e), marks = Math.max(torn, level(e));
        int skin = Skin.of(e);
        if (torn > 0) draw(pose, buffers, tex[TEARS + torn - 1], light, overlay, skin, 1f);   // their own skin through the rips (they stay until mended)
        if (marks > 0) {
            draw(pose, buffers, tex[EDGES + marks - 1], light, overlay, 0xFFFFFF, 1f);      // the rips' edges, grime
            draw(pose, buffers, tex[BRUISES + marks - 1], light, overlay, bruise(skin), 1f);
            draw(pose, buffers, tex[CUTS + marks - 1], light, overlay, BloodFx.color(e), 1f);
        }
        float dust = dust(e, ageInTicks);
        if (dust > 0.02f) draw(pose, buffers, DUST_TEX[hd], light, overlay, 0xFFFFFF, Math.min(1f, dust));
    }

    /** Builds up with heavy blows and hard landings, wears off over a minute, washes off in water. */
    private static float dust(LivingEntity e, float age) {
        Dust d = DUST.computeIfAbsent(e, k -> new Dust());
        float dt = Float.isNaN(d.lastAge) ? 0 : Math.max(0, Math.min(20, age - d.lastAge));
        d.lastAge = age;
        boolean hurt = e.hurtTime > 0;
        if (hurt && !d.wasHurt) d.amount += e.hurtTime >= 8 ? 0.3f : 0.15f;     // a fresh blow
        d.wasHurt = hurt;
        boolean air = !e.onGround();
        if (air) d.fall = Math.max(d.fall, e.fallDistance);
        else if (d.wasAirborne && d.fall > 4) d.amount += Math.min(0.6f, d.fall * 0.04f);   // crashed into the ground
        if (!air) d.fall = 0;
        d.wasAirborne = air;
        d.amount = Math.min(1.2f, d.amount) - dt * (e.isInWater() ? 0.05f : 1 / 1200f);
        if (d.amount < 0) d.amount = 0;
        return d.amount;
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, int light, int overlay, int tint, float alpha) {
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, overlay,
                ((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, alpha);
    }
}
