package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.PublicStatePacket;
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
 * Battle damage (CX-47): a fighter shows the fight on them. Below 70% health the clothes start to rip (skin showing
 * through, in the fighter's own skin tone), with frayed edges, scratches and grime; below 45% and 20% more of it, and
 * bruises. Heavy blows and hard landings cover them in dust, which wears off over a minute (at once in water). The rips
 * mend as health comes back. Textures from {@code tools/BattleDamageGen.java}; client config {@code battleDamage}.
 */
public class BattleDamageLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation[][] TEX = {textures("battle"), textures("battle_hd")};
    private static final int DEFAULT_SKIN = 0xD9A87A;

    private static ResourceLocation[] textures(String folder) {
        ResourceLocation[] t = new ResourceLocation[7];
        for (int i = 1; i <= 3; i++) {
            t[i - 1] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/" + folder + "/tears_" + i + ".png");
            t[i + 2] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/" + folder + "/marks_" + i + ".png");
        }
        t[6] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/" + folder + "/dust.png");
        return t;
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

    /** 0 unhurt .. 3 in tatters, from health. */
    public static int level(LivingEntity e) {
        float max = e.getMaxHealth();
        if (max <= 0) return 0;
        float r = e.getHealth() / max;
        return r < 0.2f ? 3 : r < 0.45f ? 2 : r < 0.7f ? 1 : 0;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (e.isInvisible() || !DBZConfig.CLIENT.battleDamage.get()) return;
        ResourceLocation[] tex = TEX[ArtStyle.hiRes() ? 1 : 0];
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0);
        int level = level(e);
        if (level > 0) {
            PublicStatePacket state = ClientPublicStates.get(e.getId());
            int skin = state != null && state.skinTone() >= 0 ? state.skinTone() : DEFAULT_SKIN;
            draw(pose, buffers, tex[level - 1], light, overlay, skin, 1f);       // skin through the rips
            draw(pose, buffers, tex[level + 2], light, overlay, 0xFFFFFF, 1f);   // their edges, scratches, bruises, grime
        }
        float dust = dust(e, ageInTicks);
        if (dust > 0.02f) draw(pose, buffers, tex[6], light, overlay, 0xFFFFFF, Math.min(1f, dust));
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
