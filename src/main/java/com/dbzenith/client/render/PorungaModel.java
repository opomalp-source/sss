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
 * Porunga, the dragon of Namek's Dragon Balls (v0.167.0; after the user's reference pictures): a serpent's tail coiled
 * below (the {@code segment} and {@code spike} repeated like the Eternal Dragon's), and rising from it a huge hunched
 * body: green pebbled hide over a pale chest and belly, great humps of shoulder rising either side of the head, dark
 * spikes on them and down the back, heavy arms hanging forward with clawed hands. The head sits low between the
 * shoulders: a broad face, a heavy brow over red eyes, a wide mouth with fangs, long pointed ears straight out to the
 * sides, two dark horns. Torso pivot at the waist, head pivot where it meets the chest; facing -Z, y down. Texture:
 * tools/ArtGen.java (128x128).
 */
public final class PorungaModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "porunga"), "main");
    public static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/porunga.png");
    /** Where the head sits on the torso (model pixels above the waist, and forward). */
    public static final float NECK_UP = 20f, NECK_FORWARD = 2.5f;

    public final ModelPart segment, spike, torso, head, eyes;

    public PorungaModel(ModelPart root) {
        segment = root.getChild("segment");
        spike = root.getChild("spike");
        torso = root.getChild("torso");
        head = root.getChild("head");
        eyes = root.getChild("eyes");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("segment", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -6, 8, 8, 12), PartPose.ZERO);
        root.addOrReplaceChild("spike", CubeListBuilder.create().texOffs(40, 0).addBox(-1, -8, -1, 2, 4, 2), PartPose.ZERO);

        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create()
                        .texOffs(0, 20).addBox(-6, -10, -4, 12, 10, 8)                      // belly
                        .texOffs(0, 38).addBox(-9, -22, -5, 18, 12, 10)                     // chest
                        .texOffs(0, 62).addBox(-7, -26, -1, 14, 5, 7),                      // the hump behind the head
                PartPose.ZERO);
        for (int i = 0; i < 4; i++)                                                         // dark spikes down the back
            torso.addOrReplaceChild("back_spine_" + i, CubeListBuilder.create().texOffs(40, 0).addBox(-1, -4, -1, 2, 4, 2),
                    PartPose.offsetAndRotation(0, -25 + i * 6, i == 0 ? 6 : 5, -0.6f, 0, 0));
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "right" : "left";
            PartDefinition shoulder = torso.addOrReplaceChild(s + "_shoulder", CubeListBuilder.create().texOffs(56, 0)
                    .addBox(-5, -6, -5, 10, 10, 10), PartPose.offsetAndRotation(side * 11, -21, 0.5f, 0, 0, side * 0.12f));   // rising past the head
            for (int k = 0; k < 2; k++)
                shoulder.addOrReplaceChild(s + "_spike_" + k, CubeListBuilder.create().texOffs(40, 0).addBox(-1, -4, -1, 2, 4, 2),
                        PartPose.offsetAndRotation(side * (1 + k * 3), -6, 1 + k, -0.3f, 0, side * (0.5f + k * 0.3f)));
            PartDefinition arm = shoulder.addOrReplaceChild(s + "_arm", CubeListBuilder.create().texOffs(96, 0)
                    .addBox(-3.5f, 0, -3.5f, 7, 12, 7), PartPose.offsetAndRotation(side * 1.5f, 2, 0, -0.25f, 0, -side * 0.28f));
            PartDefinition forearm = arm.addOrReplaceChild(s + "_forearm", CubeListBuilder.create().texOffs(96, 20)
                    .addBox(-3, 0, -3, 6, 11, 6), PartPose.offsetAndRotation(0, 11, 0, -0.55f, 0, side * 0.2f));
            forearm.addOrReplaceChild(s + "_hand", CubeListBuilder.create().texOffs(96, 38).addBox(-3, 0, -3, 6, 4, 6),   // claws painted on
                    PartPose.offsetAndRotation(0, 10.5f, 0, -0.2f, 0, 0));
        }

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 76).addBox(-6, -9, -5, 12, 8, 10)                      // skull, broad
                        .texOffs(44, 76).addBox(-5, -5, -8, 10, 5, 3)                      // muzzle, wide and short
                        .texOffs(44, 86).addBox(-6, -9, -7, 12, 2, 2),                      // heavy brow
                PartPose.ZERO);
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(72, 76).addBox(-4.5f, 0, -5, 9, 2, 5),
                PartPose.offsetAndRotation(0, 0, -4, 0.15f, 0, 0));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(100, 76).addBox(-9, -1, -1, 9, 2, 2),   // straight out to the sides
                PartPose.offsetAndRotation(-5.5f, -6, -1, 0, 0, 0.15f));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(100, 82).addBox(0, -1, -1, 9, 2, 2),
                PartPose.offsetAndRotation(5.5f, -6, -1, 0, 0, -0.15f));
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "right" : "left";
            PartDefinition horn = head.addOrReplaceChild(s + "_horn", CubeListBuilder.create().texOffs(0, 94).addBox(-1, -6, -1, 2, 6, 2),
                    PartPose.offsetAndRotation(side * 3, -8.5f, 0, -0.35f, 0, side * 0.2f));
            horn.addOrReplaceChild(s + "_horn_tip", CubeListBuilder.create().texOffs(8, 94).addBox(-0.75f, -5, -0.75f, 1.5f, 5, 1.5f),
                    PartPose.offsetAndRotation(0, -5.5f, 0, -0.5f, 0, 0));                 // curving back
        }
        for (int i = 0; i < 2; i++)
            head.addOrReplaceChild("crest_" + i, CubeListBuilder.create().texOffs(40, 0).addBox(-1, -3, -1, 2, 3, 2),
                    PartPose.offsetAndRotation(0, -8.5f, 1 + i * 2.5f, -0.4f, 0, 0));
        root.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-5, -7, -6, 3, 1.5f, 1).addBox(2, -7, -6, 3, 1.5f, 1), PartPose.ZERO);   // drawn full bright, over the muzzle
        return LayerDefinition.create(mesh, 128, 128);
    }
}
