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
 * Racial features built in code (placeholder geometry): Namekian antennae, Frost Demon horns, Majin head
 * tentacle (head-anchored) and the Saiyan tail (body-anchored, swaying).
 */
public class RaceFeatureModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "race_features"), "main");

    private final ModelPart head;
    private final ModelPart antennae;
    private final ModelPart horns;
    private final ModelPart tentacle;
    private final ModelPart ears;
    private final ModelPart demonHorns;
    private final ModelPart wings;
    private final ModelPart body;
    private final ModelPart tailBase;
    private final ModelPart tailMid;
    private final ModelPart tailTip;

    public RaceFeatureModel(ModelPart root) {
        head = root.getChild("head");
        antennae = head.getChild("antennae");
        horns = head.getChild("horns");
        tentacle = head.getChild("tentacle");
        ears = head.getChild("ears");
        demonHorns = head.getChild("demon_horns");
        body = root.getChild("body");
        wings = body.getChild("wings");
        tailBase = body.getChild("tail_base");
        tailMid = tailBase.getChild("tail_mid");
        tailTip = tailMid.getChild("tail_tip");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);

        // Two thin stalks rising from the forehead, bending back, with small bulbs.
        PartDefinition antennae = head.addOrReplaceChild("antennae", CubeListBuilder.create(), PartPose.ZERO);
        antennae.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.5f, -6, -0.5f, 1, 6, 1).addBox(-0.75f, -7, -0.75f, 1.5f, 1.2f, 1.5f),
                PartPose.offsetAndRotation(-1.5f, -8, -3.5f, 0.5f, 0, -0.15f));
        antennae.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.5f, -6, -0.5f, 1, 6, 1).addBox(-0.75f, -7, -0.75f, 1.5f, 1.2f, 1.5f),
                PartPose.offsetAndRotation(1.5f, -8, -3.5f, 0.5f, 0, 0.15f));

        // Horns on both sides of the head, angled out and up.
        PartDefinition horns = head.addOrReplaceChild("horns", CubeListBuilder.create(), PartPose.ZERO);
        horns.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1, -1, -1, 3, 2, 2).addBox(1.5f, -4, -0.75f, 1.5f, 3.5f, 1.5f).addBox(2, -6, -0.5f, 1, 2.2f, 1),
                PartPose.offsetAndRotation(-5, -6, 0, 0, 0, -0.6f));
        horns.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0).mirror()
                        .addBox(-2, -1, -1, 3, 2, 2).addBox(-3, -4, -0.75f, 1.5f, 3.5f, 1.5f).addBox(-3, -6, -0.5f, 1, 2.2f, 1),
                PartPose.offsetAndRotation(5, -6, 0, 0, 0, 0.6f));

        // One thick, curling tentacle from the crown.
        PartDefinition tentacle = head.addOrReplaceChild("tentacle", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.25f, -4, -1.25f, 2.5f, 4, 2.5f), PartPose.offsetAndRotation(0, -8, -0.5f, -0.2f, 0, 0));
        tentacle.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1, -3.5f, -1, 2, 3.5f, 2), PartPose.offsetAndRotation(0, -4, 0, 0.9f, 0, 0));
        tentacle.getChild("mid").addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.75f, -3, -0.75f, 1.5f, 3, 1.5f), PartPose.offsetAndRotation(0, -3.5f, 0, 0.9f, 0, 0));

        // Long pointed ears swept back from the sides of the head.
        PartDefinition ears = head.addOrReplaceChild("ears", CubeListBuilder.create(), PartPose.ZERO);
        ears.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.5f, -1.5f, 0, 1, 3, 2).addBox(-0.4f, -2.6f, 1.6f, 0.8f, 2, 1.6f).addBox(-0.3f, -3.4f, 3, 0.6f, 1.2f, 1),
                PartPose.offsetAndRotation(-4.3f, -4, -0.5f, 0.35f, -0.35f, -0.1f));
        ears.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.5f, -1.5f, 0, 1, 3, 2).addBox(-0.4f, -2.6f, 1.6f, 0.8f, 2, 1.6f).addBox(-0.3f, -3.4f, 3, 0.6f, 1.2f, 1),
                PartPose.offsetAndRotation(4.3f, -4, -0.5f, 0.35f, 0.35f, 0.1f));

        // Two short horns curling up from the forehead.
        PartDefinition demonHorns = head.addOrReplaceChild("demon_horns", CubeListBuilder.create(), PartPose.ZERO);
        demonHorns.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.75f, -2, -0.75f, 1.5f, 2, 1.5f).addBox(-0.5f, -3.6f, -0.2f, 1, 1.8f, 1).addBox(-0.3f, -4.6f, 0.5f, 0.6f, 1.2f, 0.6f),
                PartPose.offsetAndRotation(-2.2f, -7.6f, -2.8f, -0.2f, 0, -0.35f));
        demonHorns.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.75f, -2, -0.75f, 1.5f, 2, 1.5f).addBox(-0.5f, -3.6f, -0.2f, 1, 1.8f, 1).addBox(-0.3f, -4.6f, 0.5f, 0.6f, 1.2f, 0.6f),
                PartPose.offsetAndRotation(2.2f, -7.6f, -2.8f, -0.2f, 0, 0.35f));

        // Tail: three segments from the lower back, hanging down and curling.
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition base = body.addOrReplaceChild("tail_base", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1, 0, -1, 2, 5, 2), PartPose.offsetAndRotation(0, 10.5f, 2, 0.6f, 0, 0));
        PartDefinition mid = base.addOrReplaceChild("tail_mid", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1, 0, -1, 2, 5, 2), PartPose.offsetAndRotation(0, 5, 0, 0.5f, 0, 0));
        mid.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.25f, 0, -1.25f, 2.5f, 4, 2.5f), PartPose.offsetAndRotation(0, 5, 0, 0.6f, 0, 0));
        // Bio-Android wings: two folded carapace blades on the upper back.
        PartDefinition wings = body.addOrReplaceChild("wings", CubeListBuilder.create(), PartPose.ZERO);
        wings.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1, 0, 0, 2, 9, 0.8f).addBox(-0.7f, 9, 0, 1.4f, 3, 0.8f),
                PartPose.offsetAndRotation(-1.6f, 0.5f, 2.2f, 0.18f, 0, 0.22f));
        wings.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1, 0, 0, 2, 9, 0.8f).addBox(-0.7f, 9, 0, 1.4f, 3, 0.8f),
                PartPose.offsetAndRotation(1.6f, 0.5f, 2.2f, 0.18f, 0, -0.22f));
        return LayerDefinition.create(mesh, 16, 16);
    }

    public void follow(ModelPart parentHead, ModelPart parentBody) {
        head.copyFrom(parentHead);
        body.copyFrom(parentBody);
    }

    public void renderFeature(PoseStack pose, VertexConsumer vc, int light, int overlay, RaceTraits.Feature feature, float r, float g, float b) {
        antennae.visible = feature == RaceTraits.Feature.ANTENNAE;
        horns.visible = feature == RaceTraits.Feature.HORNS;
        tentacle.visible = feature == RaceTraits.Feature.TENTACLE;
        ears.visible = feature == RaceTraits.Feature.EARS;
        demonHorns.visible = feature == RaceTraits.Feature.DEMON_HORNS;
        if (feature == RaceTraits.Feature.WINGS) {
            tailBase.visible = false;                            // the body tree carries the tail too
            wings.visible = true;
            body.render(pose, vc, light, overlay, r, g, b, 1f);
            tailBase.visible = true;
            return;
        }
        if (feature != RaceTraits.Feature.NONE) head.render(pose, vc, light, overlay, r, g, b, 1f);
    }

    public void renderTail(PoseStack pose, VertexConsumer vc, int light, int overlay, float ageInTicks, float r, float g, float b) {
        wings.visible = false;
        float sway = Mth.sin(ageInTicks * 0.12f);
        tailBase.yRot = sway * 0.35f;
        tailMid.zRot = sway * 0.25f;
        tailTip.zRot = sway * 0.35f;
        body.render(pose, vc, light, overlay, r, g, b, 1f);
    }
}
