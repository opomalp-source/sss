package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.race.RaceTraits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Racial body parts in 3D (CX-14d): Namekian antennae and pointed ears, Frost Demon horns and ear plates, the Majin
 * head tentacle, demon horns, Bio-Android wings and the Saiyan tail (five furry segments that hang and curl, lift
 * behind a runner, stream behind a flier, sway, and wrap round the waist while crouching). Parts are greyscale
 * materials from textures/entity/race_parts.png (skin, fur, bone, carapace), tinted per race and form.
 */
public class RaceFeatureModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "race_features"), "main");
    /** Material tiles in race_parts.png (texture units of a 64x64 sheet): skin, bone and shell on the top row (16 wide). */
    static final int SKIN_U = 0, BONE_U = 16, SHELL_U = 32;              // fur is the wide row at v 16
    static final int TAIL_SEGMENTS = 5;

    private final ModelPart head;
    private final ModelPart antennae;
    private final ModelPart horns;
    private final ModelPart tentacle;
    private final ModelPart ears;
    private final ModelPart demonHorns;
    private final ModelPart crest;
    private final ModelPart crestRidge;
    private final ModelPart browRidge;
    private final ModelPart wings;
    private final ModelPart body;
    private final ModelPart wrap;
    private final ModelPart[] tail = new ModelPart[TAIL_SEGMENTS];

    public RaceFeatureModel(ModelPart root) {
        head = root.getChild("head");
        antennae = head.getChild("antennae");
        horns = head.getChild("horns");
        tentacle = head.getChild("tentacle");
        ears = head.getChild("ears");
        demonHorns = head.getChild("demon_horns");
        crest = head.getChild("crest");
        crestRidge = head.getChild("crest_ridge");
        browRidge = head.getChild("brow_ridge");
        body = root.getChild("body");
        wings = body.getChild("wings");
        wrap = body.getChild("tail_wrap");
        ModelPart p = body;
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            p = p.getChild("tail_" + i);
            tail[i] = p;
        }
    }

    /** A chain of tapering boxes, each child bent from the last: for antennae, horns, tentacles, ears. */
    private static PartDefinition chain(PartDefinition parent, String name, int u, PartPose rootPose, float[][] links) {
        PartDefinition p = parent;
        for (int i = 0; i < links.length; i++) {
            float[] l = links[i];                                                  // width, length, depth, xRot, yRot, zRot
            float w = l[0], len = l[1], d = l[2];
            PartPose pose = i == 0 ? rootPose : PartPose.offsetAndRotation(0, -links[i - 1][1] + 0.15f, 0, l[3], l[4], l[5]);
            p = p.addOrReplaceChild(i == 0 ? name : name + "_" + i, CubeListBuilder.create().texOffs(u, 0)
                    .addBox(-w / 2, -len, -d / 2, w, len, d), pose);
        }
        return p;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);

        // Namekian antennae: thin stalks from the top of the forehead, leaning forward then sweeping back, bulbed.
        PartDefinition antennae = head.addOrReplaceChild("antennae", CubeListBuilder.create(), PartPose.ZERO);
        for (int s = -1; s <= 1; s += 2) {
            PartDefinition tip = chain(antennae, s < 0 ? "left" : "right", SKIN_U, PartPose.offsetAndRotation(s * 1.4f, -7.8f, -3.2f, 0.35f, 0, s * 0.12f),
                    new float[][]{{1.1f, 3f, 1.1f}, {0.9f, 2.6f, 0.9f, -0.75f, 0, s * 0.1f}, {0.8f, 1.8f, 0.8f, -0.6f, 0, 0}});
            tip.addOrReplaceChild("bulb", CubeListBuilder.create().texOffs(SKIN_U, 0).addBox(-0.75f, -1.3f, -0.75f, 1.5f, 1.3f, 1.5f),
                    PartPose.offset(0, -1.6f, 0));
        }

        // Pointed ears: a leaf sweeping up and back from each side of the head (Namekians, and anyone who picks them).
        PartDefinition ears = head.addOrReplaceChild("ears", CubeListBuilder.create(), PartPose.ZERO);
        for (int s = -1; s <= 1; s += 2) {
            PartDefinition e = ears.addOrReplaceChild(s < 0 ? "left" : "right", CubeListBuilder.create().texOffs(SKIN_U, 0)
                    .addBox(-0.5f, -2f, -1.5f, 1, 4f, 3.2f), PartPose.offsetAndRotation(s * 4.4f, -3.4f, 0.2f, -0.3f, s * -0.3f, s * 0.4f));
            PartDefinition m = e.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(SKIN_U, 0)
                    .addBox(-0.4f, -3.4f, -1.1f, 0.8f, 3.4f, 2.4f), PartPose.offsetAndRotation(0, -1.8f, 0.6f, -0.4f, 0, 0));
            m.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(SKIN_U, 0)
                    .addBox(-0.3f, -3f, -0.7f, 0.6f, 3f, 1.4f), PartPose.offsetAndRotation(0, -3.2f, 0.4f, -0.45f, 0, 0));
        }

        // Frost Demon horns: thick at the root on the sides of the skull, curving out and up to a point; ear plates below.
        PartDefinition horns = head.addOrReplaceChild("horns", CubeListBuilder.create(), PartPose.ZERO);
        for (int s = -1; s <= 1; s += 2) {
            PartDefinition h = horns.addOrReplaceChild(s < 0 ? "left" : "right", CubeListBuilder.create().texOffs(BONE_U, 0)
                    .addBox(-1.3f, -1.3f, -1.3f, 2.6f, 2.6f, 2.6f), PartPose.offsetAndRotation(s * 4.6f, -6.6f, 0.4f, 0, 0, s * 1.25f));
            PartDefinition m = h.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(BONE_U, 0)
                    .addBox(-1f, -3f, -1f, 2f, 3f, 2f), PartPose.offsetAndRotation(0, -1f, 0, 0.1f, 0, s * -0.7f));
            PartDefinition t = m.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(BONE_U, 0)
                    .addBox(-0.7f, -2.6f, -0.7f, 1.4f, 2.6f, 1.4f), PartPose.offsetAndRotation(0, -2.9f, 0, 0.15f, 0, s * -0.45f));
            t.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(BONE_U, 0)
                    .addBox(-0.4f, -1.8f, -0.4f, 0.8f, 1.8f, 0.8f), PartPose.offsetAndRotation(0, -2.5f, 0, 0.2f, 0, s * -0.3f));
            horns.addOrReplaceChild(s < 0 ? "plate_left" : "plate_right", CubeListBuilder.create().texOffs(SHELL_U, 0)
                    .addBox(-0.4f, -1.4f, -1.6f, 0.8f, 2.8f, 3.2f), PartPose.offset(s * 4.25f, -3.4f, 0.6f));
        }

        // Majin tentacle: thick at the crown, curling back and down in four tapering links.
        chain(head, "tentacle", SKIN_U, PartPose.offsetAndRotation(0, -7.7f, 0.4f, 0.05f, 0, 0),
                new float[][]{{3.2f, 3.2f, 3.2f}, {2.6f, 3f, 2.6f, -0.35f, 0, 0}, {2.1f, 2.8f, 2.1f, -0.9f, 0, 0}, {1.6f, 2.6f, 1.6f, -1.0f, 0, 0}, {1.1f, 1.8f, 1.1f, -0.7f, 0, 0}});

        // Demon horns: two from the forehead, ridged, curling back.
        PartDefinition demonHorns = head.addOrReplaceChild("demon_horns", CubeListBuilder.create(), PartPose.ZERO);
        for (int s = -1; s <= 1; s += 2) {
            chain(demonHorns, s < 0 ? "left" : "right", BONE_U, PartPose.offsetAndRotation(s * 2.3f, -7.7f, -2.6f, 0.2f, 0, s * 0.35f),
                    new float[][]{{1.9f, 2f, 1.9f}, {1.4f, 1.9f, 1.4f, -0.55f, 0, s * -0.1f}, {0.9f, 1.7f, 0.9f, -0.6f, 0, 0}, {0.5f, 1.2f, 0.5f, -0.5f, 0, 0}});
        }

        // Frost Demon third form: the skull swept back into a long crest, a ridge along its top.
        PartDefinition crest = head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-3.5f, -3.4f, 0, 7f, 4.6f, 7.5f), PartPose.offsetAndRotation(0, -4.4f, 1.2f, 0.22f, 0, 0));
        crest.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 32).addBox(-2.4f, -2.6f, 0, 4.8f, 3.2f, 4.2f),
                PartPose.offsetAndRotation(0, 0, 7.2f, 0.18f, 0, 0));
        PartDefinition ridge = head.addOrReplaceChild("crest_ridge", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-2.6f, -4.3f, -0.5f, 5.2f, 1.4f, 8.5f), PartPose.offsetAndRotation(0, -4.4f, 1.2f, 0.22f, 0, 0));
        ridge.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 32).addBox(-1.6f, -3.4f, 0, 3.2f, 1.2f, 4.4f),
                PartPose.offsetAndRotation(0, 0, 7.6f, 0.18f, 0, 0));
        // Super Saiyan 3: a heavy ridge over the eyes where the brows were.
        head.addOrReplaceChild("brow_ridge", CubeListBuilder.create().texOffs(SKIN_U, 0).addBox(-3.7f, -5.5f, -4.45f, 7.4f, 1f, 0.7f),
                PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        // Saiyan tail: five furry links from the base of the spine.
        PartDefinition p = body;
        float[] w = {2.3f, 2.2f, 2.1f, 2.0f, 2.2f};
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            PartPose pose = i == 0 ? PartPose.offsetAndRotation(0, 10.6f, 2.2f, 0.5f, 0, 0) : PartPose.offsetAndRotation(0, 2.9f, 0, 0.2f, 0, 0);
            p = p.addOrReplaceChild("tail_" + i, CubeListBuilder.create().texOffs(0, 16)
                    .addBox(-w[i] / 2, 0, -w[i] / 2, w[i], 3.2f, w[i]), pose);
        }
        // The tail wrapped round the waist like a belt, its tip tucked at the back.
        body.addOrReplaceChild("tail_wrap", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4.7f, 9.7f, -2.7f, 9.4f, 1.9f, 5.4f)
                .addBox(1.2f, 10.9f, 2.5f, 1.8f, 2.6f, 1.8f), PartPose.ZERO);

        // Bio-Android wings: two carapace blades folded on the upper back, flaring out at the tips.
        PartDefinition wings = body.addOrReplaceChild("wings", CubeListBuilder.create(), PartPose.ZERO);
        for (int s = -1; s <= 1; s += 2) {
            chain(wings, s < 0 ? "left" : "right", SHELL_U, PartPose.offsetAndRotation(s * 1.7f, 1.2f, 2.3f, 0.2f + (float) Math.PI, 0, s * -0.22f),
                    new float[][]{{3f, 6f, 0.7f}, {2.4f, 4.5f, 0.6f, -0.12f, 0, s * -0.12f}, {1.4f, 2.5f, 0.5f, -0.1f, 0, s * -0.1f}});
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void follow(ModelPart parentHead, ModelPart parentBody) {
        head.copyFrom(parentHead);
        body.copyFrom(parentBody);
    }

    public void renderFeature(PoseStack pose, VertexConsumer vc, int light, int overlay, RaceTraits.Feature feature, boolean withEars,
                              float r, float g, float b) {
        renderFeature(pose, vc, light, overlay, feature, withEars, 1f, r, g, b);
    }

    /** {@code hornScale}: Frost Demon horn size for the form (0 hides them, leaving the ear plates). */
    public void renderFeature(PoseStack pose, VertexConsumer vc, int light, int overlay, RaceTraits.Feature feature, boolean withEars,
                              float hornScale, float r, float g, float b) {
        renderFeature(pose, vc, light, overlay, feature, withEars, hornScale, 1f, r, g, b);
    }

    /** Sets a part's length (CX-16b: long, short or none): stretched along its own length, a little slimmer when short. */
    private static void length(ModelPart p, float length) {
        p.visible = length > 0;
        float girth = length < 1 ? 0.8f + 0.2f * length : 1f;
        p.xScale = girth;
        p.zScale = girth;
        p.yScale = Math.max(0.01f, length);
    }

    /** {@code length}: the chosen style's size for antennae, the tentacle, ears, demon horns and wings (1 classic). */
    public void renderFeature(PoseStack pose, VertexConsumer vc, int light, int overlay, RaceTraits.Feature feature, boolean withEars,
                              float hornScale, float length, float r, float g, float b) {
        for (String side : new String[]{"left", "right"}) {
            length(antennae.getChild(side), length);
            length(ears.getChild(side), feature == RaceTraits.Feature.EARS ? length : 1f);   // a Namekian's ears keep their size
            length(demonHorns.getChild(side), length);
            length(wings.getChild(side), length);
        }
        length(tentacle, length);
        for (String plate : new String[]{"plate_left", "plate_right"}) horns.getChild(plate).visible = false;   // drawn on their own, in the shell colour
        crest.visible = false;
        crestRidge.visible = false;
        browRidge.visible = false;
        for (String side : new String[]{"left", "right"}) {
            ModelPart h = horns.getChild(side);
            h.visible = hornScale > 0;
            h.xScale = h.yScale = h.zScale = Math.max(0.01f, hornScale);
        }
        antennae.visible = feature == RaceTraits.Feature.ANTENNAE;
        horns.visible = feature == RaceTraits.Feature.HORNS;
        tentacle.visible = feature == RaceTraits.Feature.TENTACLE;
        ears.visible = feature == RaceTraits.Feature.EARS || withEars;
        demonHorns.visible = feature == RaceTraits.Feature.DEMON_HORNS;
        if (feature == RaceTraits.Feature.WINGS) {
            tail[0].visible = false;                              // the body tree carries the tail too
            wrap.visible = false;
            wings.visible = true;
            body.render(pose, vc, light, overlay, r, g, b, 1f);
            tail[0].visible = true;
            return;
        }
        if (feature != RaceTraits.Feature.NONE || withEars) head.render(pose, vc, light, overlay, r, g, b, 1f);
    }

    /** A Frost Demon's ear plates on their own (they are shell, coloured like it). */
    public void renderHornPlates(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        for (ModelPart p : new ModelPart[]{antennae, tentacle, ears, demonHorns, crest, crestRidge, browRidge}) p.visible = false;
        horns.visible = true;
        horns.getChild("left").visible = false;
        horns.getChild("right").visible = false;
        horns.getChild("plate_left").visible = true;
        horns.getChild("plate_right").visible = true;
        head.render(pose, vc, light, overlay, r, g, b, 1f);
        horns.getChild("plate_left").visible = false;
        horns.getChild("plate_right").visible = false;
    }

    /** One extra head shape on its own: "crest" or "brow_ridge". */
    public void renderHeadShape(PoseStack pose, VertexConsumer vc, int light, int overlay, String which, float r, float g, float b) {
        for (ModelPart p : new ModelPart[]{antennae, horns, tentacle, ears, demonHorns}) p.visible = false;
        crest.visible = which.equals("crest");
        crestRidge.visible = which.equals("crest_ridge");
        browRidge.visible = which.equals("brow_ridge");
        head.render(pose, vc, light, overlay, r, g, b, 1f);
        crest.visible = false;
        crestRidge.visible = false;
        browRidge.visible = false;
    }

    /**
     * @param lift   0 hanging .. 1 straight out behind (running, falling, flying)
     * @param wrapped wound round the waist instead
     */
    public void renderTail(PoseStack pose, VertexConsumer vc, int light, int overlay, float ageInTicks, float lift, boolean wrapped,
                           float r, float g, float b) {
        renderTail(pose, vc, light, overlay, ageInTicks, lift, wrapped, 1f, r, g, b);
    }

    /** {@code size}: a bigger tail for the Primal clans (thicker and longer as one). */
    public void renderTail(PoseStack pose, VertexConsumer vc, int light, int overlay, float ageInTicks, float lift, boolean wrapped, float size,
                           float r, float g, float b) {
        tail[0].xScale = tail[0].yScale = tail[0].zScale = size;
        wings.visible = false;
        wrap.visible = wrapped;
        tail[0].visible = !wrapped;
        if (!wrapped) {
            float calm = 1 - Mth.clamp(lift, 0, 1);
            tail[0].xRot = 0.45f + 1.05f * lift;
            for (int i = 0; i < TAIL_SEGMENTS; i++) {
                float wave = Mth.sin(ageInTicks * (0.11f + 0.05f * lift) - i * 0.7f);
                if (i > 0) tail[i].xRot = 0.32f * calm - 0.05f * lift + 0.08f * Mth.sin(ageInTicks * 0.07f - i);   // a lazy curl when hanging
                tail[i].zRot = wave * (0.12f + 0.08f * i) * (0.6f + 0.4f * calm);
                if (i == 0) tail[i].yRot = wave * 0.25f;
            }
        }
        body.render(pose, vc, light, overlay, r, g, b, 1f);
    }
}
