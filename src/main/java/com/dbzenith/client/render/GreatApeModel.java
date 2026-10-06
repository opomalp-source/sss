package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The Great Ape: a hunched, heavy ape with long knuckle-dragging arms, short legs, a muzzled head and a tail
 * (original design; texture painted by tools/ArtGen.java, 128x64). Model units are vanilla pixels, feet at y = 24.
 */
public class GreatApeModel extends EntityModel<Player> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "great_ape"), "main");
    public static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/great_ape.png");
    private static final ResourceLocation GOLDEN = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/great_ape_golden.png");
    private static final ResourceLocation LEGENDARY = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/great_ape_legendary.png");

    /** The fur for an ape form: brown under the moon, gold for the golden ape, a green-gold for the legendary one. */
    public static ResourceLocation texture(String form) {
        return "golden_ape".equals(form) ? GOLDEN : "legendary_great_ape".equals(form) ? LEGENDARY : TEXTURE;
    }

    private final ModelPart body, head, rightArm, leftArm, rightLeg, leftLeg, tail;

    public GreatApeModel(ModelPart root) {
        body = root.getChild("body");
        head = root.getChild("head");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        tail = root.getChild("tail");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-7, 0, -4.5f, 14, 13, 9),
                PartPose.offsetAndRotation(0, 3, 0, 0.35f, 0, 0));                          // hunched barrel chest
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.5f, -8, -6, 9, 8, 8)                       // skull
                        .texOffs(36, 0).addBox(-3, -3.5f, -9, 6, 4, 3)                      // muzzle
                        .texOffs(36, 8).addBox(-4.5f, -6, -7, 9, 1, 1),                     // brow ridge
                PartPose.offset(0, 4, -5));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(58, 0).addBox(-1, -2, -1, 1, 3, 2),
                PartPose.offset(-4.5f, -5, -1));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(58, 0).mirror().addBox(0, -2, -1, 1, 3, 2),
                PartPose.offset(4.5f, -5, -1));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(48, 16).addBox(-4, -1, -2.5f, 5, 18, 5),
                PartPose.offset(-8, 5, -2));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(48, 16).mirror().addBox(-1, -1, -2.5f, 5, 18, 5),
                PartPose.offset(8, 5, -2));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(70, 16).addBox(-2.5f, 0, -2.5f, 5, 10, 5),
                PartPose.offset(-4, 14, 1));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(70, 16).mirror().addBox(-2.5f, 0, -2.5f, 5, 10, 5),
                PartPose.offset(4, 14, 1));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(92, 16).addBox(-1, -1, 0, 2, 2, 8),
                PartPose.offsetAndRotation(0, 14, 4, -0.6f, 0, 0));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(Player player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        float swing = Mth.cos(limbSwing * 0.5f) * 1.1f * limbSwingAmount;
        rightLeg.xRot = swing;
        leftLeg.xRot = -swing;
        rightArm.xRot = -swing * 0.8f - 0.15f;                                                   // arms swing against the legs
        leftArm.xRot = swing * 0.8f - 0.15f;
        rightArm.zRot = 0.08f + Mth.cos(ageInTicks * 0.06f) * 0.03f;                             // breathing sway
        leftArm.zRot = -rightArm.zRot;
        if (attackTime > 0) {                                                                    // a smashing swipe
            float a = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
            rightArm.xRot -= a * 1.6f;
        }
        tail.yRot = Mth.cos(ageInTicks * 0.1f) * 0.35f;
        body.xRot = 0.35f + Mth.cos(ageInTicks * 0.06f) * 0.02f;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        for (ModelPart p : new ModelPart[]{body, head, rightArm, leftArm, rightLeg, leftLeg, tail}) p.render(pose, vc, light, overlay, r, g, b, a);
    }
}
