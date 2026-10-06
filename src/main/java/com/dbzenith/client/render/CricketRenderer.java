package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.npc.TrainingCricket;
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
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Kai's cricket (CX-12): a little green-brown body, a round head with long antennae, big folded hind legs. */
public class CricketRenderer extends MobRenderer<TrainingCricket, CricketRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "training_cricket"), "main");
    private static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/training_cricket.png");

    public CricketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.2f);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainingCricket cricket) {
        return TEXTURE;
    }

    public static class Model extends EntityModel<TrainingCricket> {
        private final ModelPart root, leftLeg, rightLeg, antennae;

        public Model(ModelPart root) {
            this.root = root;
            this.leftLeg = root.getChild("left_leg");
            this.rightLeg = root.getChild("right_leg");
            this.antennae = root.getChild("antennae");
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -3f, -3f, 3, 2.5f, 6), PartPose.offset(0, 24, 0));
            root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 9).addBox(-1.5f, -3.4f, -5.2f, 3, 2.6f, 2.4f), PartPose.offset(0, 24, 0));
            root.addOrReplaceChild("antennae", CubeListBuilder.create().texOffs(12, 9)
                    .addBox(-1.2f, -0.2f, -6f, 0.3f, 0.3f, 6).addBox(0.9f, -0.2f, -6f, 0.3f, 0.3f, 6), PartPose.offsetAndRotation(0, 21, -5, 0.5f, 0, 0));
            for (int s = -1; s <= 1; s += 2) {                                                   // the big hind legs, folded
                root.addOrReplaceChild(s < 0 ? "left_leg" : "right_leg", CubeListBuilder.create().texOffs(0, 15)
                        .addBox(-0.5f, -0.5f, -0.5f, 1, 1, 5).addBox(-0.4f, 0.5f, 3.5f, 0.8f, 3f, 0.8f), PartPose.offsetAndRotation(s * 1.7f, 21.5f, 0, -0.25f, 0, 0));
            }
            root.addOrReplaceChild("wings", CubeListBuilder.create().texOffs(16, 0).addBox(-1.4f, -3.4f, -2.2f, 2.8f, 0.4f, 5.6f), PartPose.offset(0, 24, 0));
            return LayerDefinition.create(mesh, 32, 32);
        }

        @Override
        public void setupAnim(TrainingCricket cricket, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
            float spring = cricket.onGround() ? 0 : 0.9f;                                        // legs kick out mid-hop
            leftLeg.xRot = -0.25f + spring;
            rightLeg.xRot = -0.25f + spring;
            antennae.yRot = 0.15f * Mth.sin(age * 0.4f);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
            root.render(pose, vc, light, overlay, r, g, b, a);
        }
    }
}
