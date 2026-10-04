package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.world.SpacePodEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The Space Pod: a round white pod (three crossed boxes) with a red window, three landing legs and a thruster
 * (original design; texture from tools/ArtGen.java, 128x128). Feet at model y = 24.
 */
public class SpacePodRenderer extends EntityRenderer<SpacePodEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "space_pod"), "main");
    public static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/space_pod.png");

    private final ModelPart root;

    public SpacePodRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        root = ctx.bakeLayer(LAYER);
        shadowRadius = 0.8f;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("shell", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-12, 0, -9, 24, 16, 18)
                .texOffs(0, 34).addBox(-9, -4, -9, 18, 24, 18)
                .texOffs(0, 76).addBox(-9, 0, -12, 18, 16, 24), PartPose.offset(0, -2, 0));
        root.addOrReplaceChild("window", CubeListBuilder.create().texOffs(86, 0).addBox(-6, 2, -13, 12, 8, 1), PartPose.offset(0, -2, 0));
        root.addOrReplaceChild("thruster", CubeListBuilder.create().texOffs(86, 20).addBox(-4, 18, -4, 8, 2, 8), PartPose.offset(0, -2, 0));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(86, 10).addBox(-1, 0, -1, 2, 8, 2),
                PartPose.offsetAndRotation(-8, 16, -6, -0.35f, 0, 0.35f));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(86, 10).addBox(-1, 0, -1, 2, 8, 2),
                PartPose.offsetAndRotation(8, 16, -6, -0.35f, 0, -0.35f));
        root.addOrReplaceChild("leg_back", CubeListBuilder.create().texOffs(86, 10).addBox(-1, 0, -1, 2, 8, 2),
                PartPose.offsetAndRotation(0, 16, 9, 0.45f, 0, 0));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void render(SpacePodEntity pod, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        if (pod.state() != SpacePodEntity.PARKED) {                                            // a little shake in flight
            float t = pod.tickCount + partialTicks;
            pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * 1.3) * 2f));
        }
        pose.scale(-1f, -1f, 1f);
        pose.translate(0f, -1.501f, 0f);
        root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(pod, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpacePodEntity pod) {
        return TEXTURE;
    }
}
