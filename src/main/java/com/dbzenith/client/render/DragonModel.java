package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Parts of the Eternal Dragon (original design): a scaled body segment and dorsal spike repeated along its coil, and
 * a head with snout, open jaw, swept-back horns and whiskers. Facing -Z. Texture: tools/ArtGen.java (64x64).
 */
public final class DragonModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "eternal_dragon"), "main");
    public static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/eternal_dragon.png");

    public final ModelPart segment, spike, head, eyes;

    public DragonModel(ModelPart root) {
        segment = root.getChild("segment");
        spike = root.getChild("spike");
        head = root.getChild("head");
        eyes = root.getChild("eyes");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("segment", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -6, 8, 8, 12), PartPose.ZERO);
        root.addOrReplaceChild("spike", CubeListBuilder.create().texOffs(40, 0).addBox(-1, -8, -1, 2, 4, 2), PartPose.ZERO);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 20).addBox(-4, -3, -5, 8, 6, 10)                        // skull
                        .texOffs(36, 20).addBox(-3, -2, -13, 6, 4, 8),                      // snout
                PartPose.ZERO);
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 36).addBox(-3, 0, -8, 6, 2, 8),
                PartPose.offsetAndRotation(0, 2, -4, 0.35f, 0, 0));                          // slightly open
        head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(28, 36).addBox(-1, -1, 0, 2, 2, 8),
                PartPose.offsetAndRotation(-3, -3, 2, 0.55f, -0.25f, 0));
        head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(28, 36).mirror().addBox(-1, -1, 0, 2, 2, 8),
                PartPose.offsetAndRotation(3, -3, 2, 0.55f, 0.25f, 0));
        head.addOrReplaceChild("right_whisker", CubeListBuilder.create().texOffs(0, 46).addBox(-0.5f, -0.5f, 0, 1, 1, 12),
                PartPose.offsetAndRotation(-3, 0, -11, -0.25f, -0.9f, 0));
        head.addOrReplaceChild("left_whisker", CubeListBuilder.create().texOffs(0, 46).mirror().addBox(-0.5f, -0.5f, 0, 1, 1, 12),
                PartPose.offsetAndRotation(3, 0, -11, -0.25f, 0.9f, 0));
        root.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-4.1f, -2, -3, 1, 1, 1).addBox(3.1f, -2, -3, 1, 1, 1), PartPose.ZERO);   // drawn full bright
        return LayerDefinition.create(mesh, 64, 64);
    }
}
