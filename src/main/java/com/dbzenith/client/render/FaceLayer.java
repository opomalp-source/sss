package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.appearance.FaceParts;
import com.dbzenith.appearance.FaceParts.Part;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The face of a generated body, part by part (appearance.FaceParts): eyes, irises in the eye colour (glowing in a
 * form that has eyes of its own), brows in the hair colour (gold with a golden form), nose, mouth and an extra.
 * Drawn over the body skin, translucent so the soft parts sit on any skin tone. Pointed ears are 3D (RaceFeatureLayer).
 */
public class FaceLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[Part.values().length + 1][];
    private static final int IRIS = Part.values().length;

    static {
        for (Part p : Part.values()) {
            TEXTURES[p.ordinal()] = new ResourceLocation[p.options];
            for (int i = 0; i < p.options; i++) TEXTURES[p.ordinal()][i] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/face/" + p.key() + "_" + i + ".png");
        }
        TEXTURES[IRIS] = new ResourceLocation[Part.EYES.options];
        for (int i = 0; i < Part.EYES.options; i++) TEXTURES[IRIS][i] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/face/iris_" + i + ".png");
    }

    public FaceLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Whether this player's face is drawn by parts: the generated body, or a race skin (painted without a face). */
    public static boolean active(PublicStatePacket state) {
        return BodySkinLayer.active(state) || raceSkin(state);
    }

    static boolean raceSkin(PublicStatePacket state) {
        return state != null && state.raceLook() && RaceSkinLayer.texture(state) != null;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (!active(state)) return;
        Form form = FormHairLayer.flicker(player, state, ageInTicks);
        int face = state.face();
        int eyes = FaceParts.get(face, Part.EYES);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);

        // a race skin's own face colours, unless the player chose their own
        FaceDefaults.Face race = raceSkin(state) ? FaceDefaults.of(RaceSkinLayer.skinName(state.raceEnum(), state.variantEnum())) : null;
        boolean glowing = !form.isBase() && form.eyeColor() >= 0;
        int iris = form.eyeColor() >= 0 ? form.eyeColor() : state.eyeColor() >= 0 ? state.eyeColor() : race != null ? race.iris() : 0x1E1610;
        boolean whites = race == null || race.whites() || state.eyeColor() >= 0;
        draw(pose, buffers, Part.EYES.ordinal(), eyes, light, overlay, whites ? 0xFFFFFF : iris);     // no whites: the whole eye is dark
        draw(pose, buffers, IRIS, eyes, glowing ? LightTexture.FULL_BRIGHT : light, overlay, iris);
        int hair = form.hairColor() >= 0 ? form.hairColor() : race != null ? race.brow() : state.hairColor() >= 0 ? state.hairColor() : 0x3A2414;
        draw(pose, buffers, Part.BROWS.ordinal(), FaceParts.get(face, Part.BROWS), light, overlay, race != null && form.hairColor() < 0 ? hair : darken(hair, 0.8f));
        draw(pose, buffers, Part.NOSE.ordinal(), FaceParts.get(face, Part.NOSE), light, overlay, 0xFFFFFF);
        draw(pose, buffers, Part.MOUTH.ordinal(), FaceParts.get(face, Part.MOUTH), light, overlay, 0xFFFFFF);
        int extra = FaceParts.get(face, Part.EXTRA);
        if (extra > 0) draw(pose, buffers, Part.EXTRA.ordinal(), extra, light, overlay, 0xFFFFFF);
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, int part, int option, int light, int overlay, int tint) {
        ResourceLocation tex = TEXTURES[part][Math.max(0, Math.min(TEXTURES[part].length - 1, option))];
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, overlay,
                ((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, 1f);
    }

    private static int darken(int c, float f) {
        return (int) (((c >> 16) & 255) * f) << 16 | (int) (((c >> 8) & 255) * f) << 8 | (int) ((c & 255) * f);
    }
}
