package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.appearance.FaceParts;
import com.dbzenith.appearance.FaceParts.Part;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import net.minecraft.util.Mth;
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
    private static final int IRIS = Part.values().length, PUPIL = IRIS + 1;
    /** Classic (64x64) and HD (128x128, with a pupil-and-shine layer) part textures. */
    private static final ResourceLocation[][] TEXTURES = textures("face"), TEXTURES_HD = textures("face_hd");
    private static ResourceLocation[][] textures;
    private float opacity = 1f;

    private static ResourceLocation[][] textures(String folder) {
        ResourceLocation[][] t = new ResourceLocation[Part.values().length + 2][];
        for (Part p : Part.values()) {
            t[p.ordinal()] = new ResourceLocation[p.options];
            for (int i = 0; i < p.options; i++) t[p.ordinal()][i] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/" + folder + "/" + p.key() + "_" + i + ".png");
        }
        t[IRIS] = new ResourceLocation[Part.EYES.options];
        t[PUPIL] = new ResourceLocation[Part.EYES.options];
        for (int i = 0; i < Part.EYES.options; i++) {
            t[IRIS][i] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/" + folder + "/iris_" + i + ".png");
            t[PUPIL][i] = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/" + folder + "/pupil_" + i + ".png");
        }
        return t;
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
        boolean changingApe = ApeLook.is(state);
        opacity = 1f;
        if (changingApe) {
            float t = ApeTransformation.age(player, partialTick);
            opacity = 1 - com.dbzenith.transform.ApeChange.phase(t, 32, 64);
            if (opacity <= 0) return;
            state = ApeTransformation.before(player);
        }
        if (!active(state)) return;
        Form form = FormHairLayer.flicker(player, state, ageInTicks);
        boolean hd = ArtStyle.hiRes();
        textures = hd ? TEXTURES_HD : TEXTURES;
        int face = state.face();
        int eyes = FaceParts.get(face, Part.EYES);
        int brows = FaceParts.get(face, Part.BROWS), mouth = FaceParts.get(face, Part.MOUTH);
        // expressions (CX-14b, CX-42): what the fighter is going through shows on the face, strongest first
        boolean shout = changingApe || state.has(PublicStatePacket.CHARGING) || state.has(PublicStatePacket.TRANSFORMING);
        boolean hurt = player.hurtTime > 0;
        boolean grit = !shout && (hurt || state.has(PublicStatePacket.GUARDING) || state.has(PublicStatePacket.HEAVY));
        if (hurt || player.swinging || shout || grit) COMBAT.put(player.getId(), ageInTicks);
        Float lastFight = COMBAT.get(player.getId());
        boolean fighting = lastFight != null && ageInTicks - lastFight < 160 && ageInTicks >= lastFight;
        boolean pained = player.getHealth() < player.getMaxHealth() * 0.3f && player.getMaxHealth() > 0;
        boolean ownEyes = eyes != FaceParts.EYES_CAT;
        if (state.has(PublicStatePacket.DOWNED) || state.has(PublicStatePacket.DEAD) || com.dbzenith.client.ClientKnockouts.isDown(player.getId())) {
            eyes = FaceParts.EYES_CLOSED;                                      // out cold
            mouth = MOUTH_FROWN;
            if (brows != BROWS_NONE) brows = BROWS_WORRIED;
        } else if (shout) {
            mouth = MOUTH_SHOUT;
            if (brows != BROWS_NONE) brows = BROWS_FIERCE;
        } else if (grit) {
            mouth = MOUTH_GRIT;
            if (brows != BROWS_NONE) brows = BROWS_FIERCE;
            if (hurt && eyes != FaceParts.EYES_CLOSED) eyes = FaceParts.EYES_NARROW;
        } else if (pained) {                                                   // badly hurt: worried brows, heavy eyes, panting
            if (brows != BROWS_NONE) brows = BROWS_WORRIED;
            if (ownEyes) eyes = FaceParts.EYES_TIRED;
            mouth = (ageInTicks + player.getId() * 11) % 22 < 8 ? MOUTH_SHOUT : MOUTH_GRIT;
        } else if (state.has(PublicStatePacket.MEDITATING)) {
            eyes = FaceParts.EYES_CLOSED;                                      // at peace
            mouth = MOUTH_LINE;
        } else if (fighting) {                                                 // focused: fierce brows, a firm mouth
            if (brows != BROWS_NONE) brows = BROWS_FIERCE;
            if (mouth == MOUTH_LINE || mouth == MOUTH_SMILE) mouth = MOUTH_FROWN;
        } else if (!form.isBase() && form != Forms.GREAT_APE) {                // transformed and at ease: confident
            if (brows != BROWS_NONE) brows = BROWS_FIERCE;
            mouth = MOUTH_SMIRK;
        }
        if (!shout && !grit && eyes != FaceParts.EYES_CLOSED && ownEyes) {      // a blink now and then, sometimes two
            float cycle = (ageInTicks + player.getId() * 37) % 84;
            boolean twice = ((int) ((ageInTicks + player.getId() * 37) / 84) + player.getId()) % 4 == 0;
            if (cycle < 2.5f || twice && cycle >= 5 && cycle < 7.5f) eyes = FaceParts.EYES_CLOSED;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);

        // a race skin's own face colours, unless the player chose their own
        FaceDefaults.Face race = raceSkin(state) ? FaceDefaults.of(RaceSkinLayer.skinName(state.raceEnum(), state.variantEnum())) : null;
        boolean glowing = !form.isBase() && form.eyeColor() >= 0 || state.ultraInstinct() > 0;
        int iris = form.eyeColor() >= 0 ? form.eyeColor() : state.eyeColor() >= 0 ? state.eyeColor() : race != null ? race.iris() : 0x1E1610;
        if (state.ultraInstinct() > 0) iris = state.ultraInstinct() == 2 ? FormHairLayer.UI_EYES : FormHairLayer.UI_SIGN_EYES;   // Ultra Instinct's silver eyes
        boolean whites = race == null || race.whites() || state.eyeColor() >= 0;
        draw(pose, buffers, Part.EYES.ordinal(), eyes, light, overlay, whites ? 0xFFFFFF : iris);     // no whites: the whole eye is dark
        pose.pushPose();                                                       // the eyes glance (CX-42): irises and pupils shift a touch sideways
        float glance = glance(player, ageInTicks, eyes);
        float headYaw = getParentModel().head.yRot;
        pose.translate(glance * Mth.cos(headYaw), 0, -glance * Mth.sin(headYaw));
        draw(pose, buffers, IRIS, eyes, glowing ? LightTexture.FULL_BRIGHT : light, overlay, iris);
        if (hd) draw(pose, buffers, PUPIL, eyes, glowing ? LightTexture.FULL_BRIGHT : light, overlay, 0xFFFFFF);   // pupils and the shine
        if (glowing) {                                                         // eyes that burn: an emissive pass over the iris
            ResourceLocation glow = textures[IRIS][Math.max(0, Math.min(textures[IRIS].length - 1, eyes))];
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(glow)), LightTexture.FULL_BRIGHT, overlay,
                    ((iris >> 16) & 255) / 255f * 0.8f, ((iris >> 8) & 255) / 255f * 0.8f, (iris & 255) / 255f * 0.8f, opacity);
        }
        pose.popPose();
        int hair = form.hairColor() >= 0 ? form.hairColor() : race != null ? race.brow() : state.hairColor() >= 0 ? state.hairColor() : 0x3A2414;
        if (state.ultraInstinct() == 2) hair = FormHairLayer.UI_HAIR;              // Mastered: silver brows too
        if (!FormShape.browRidge(form.id())) draw(pose, buffers, Part.BROWS.ordinal(), brows, light, overlay, race != null && form.hairColor() < 0 ? hair : darken(hair, 0.8f));
        draw(pose, buffers, Part.NOSE.ordinal(), FaceParts.get(face, Part.NOSE), light, overlay, 0xFFFFFF);
        draw(pose, buffers, Part.MOUTH.ordinal(), mouth, light, overlay, 0xFFFFFF);
        int extra = FaceParts.get(face, Part.EXTRA);
        if (extra > 0) draw(pose, buffers, Part.EXTRA.ordinal(), extra, light, overlay, 0xFFFFFF);
    }

    /** Face part options the expressions use. */
    private static final int MOUTH_LINE = 0, MOUTH_SMILE = 1, MOUTH_GRIT = 2, MOUTH_FROWN = 3, MOUTH_SMIRK = 4, MOUTH_SHOUT = 5,
            BROWS_FIERCE = 2, BROWS_WORRIED = 3, BROWS_NONE = 5;
    /** When each fighter last fought (in their render age), for the focused look. */
    private static final java.util.Map<Integer, Float> COMBAT = new java.util.HashMap<>();

    private void draw(PoseStack pose, MultiBufferSource buffers, int part, int option, int light, int overlay, int tint) {
        ResourceLocation tex = textures[part][Math.max(0, Math.min(textures[part].length - 1, option))];
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, overlay,
                ((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, opacity);
    }


    /**
     * How far the irises shift sideways, in blocks (+ toward the fighter's left): toward the foe you are locked onto,
     * otherwise a glance to one side now and then. At most about half a face pixel, so they stay inside the eye.
     */
    private static float glance(AbstractClientPlayer player, float age, int eyes) {
        if (eyes == FaceParts.EYES_CLOSED) return 0;
        float most = 0.55f / 32f;                                              // a face pixel is half a model pixel
        net.minecraft.world.entity.LivingEntity target = player == net.minecraft.client.Minecraft.getInstance().player
                ? com.dbzenith.client.LockOn.target() : null;
        float side;
        if (target != null) {
            float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - player.getZ(), target.getX() - player.getX())) - 90;
            side = -Mth.clamp(Mth.wrapDegrees(yaw - player.getYHeadRot()) / 25f, -1, 1);
        } else {
            float t = age + player.getId() * 53;
            int pick = Math.floorMod((int) (t / 70) * 7919 + player.getId() * 31, 5);
            side = t % 70 < 38 ? new float[]{-1, 0, 0, 1, 0}[pick] : 0;
        }
        return side * most;
    }

    private static int darken(int c, float f) {
        return (int) (((c >> 16) & 255) * f) << 16 | (int) (((c >> 8) & 255) * f) << 8 | (int) ((c & 255) * f);
    }
}
