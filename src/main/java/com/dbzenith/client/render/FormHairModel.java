package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The fixed parts around the hair: a cap over the skull (hides a Minecraft skin's painted hair under the strands of
 * {@link HairMesh}) and the coloured pupils. Head space: x/z -4..4, y -8..0.
 */
public class FormHairModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "form_hair"), "main");

    private final ModelPart root;
    private final ModelPart cap;
    private final ModelPart eyes;
    private final ModelPart mass;

    public FormHairModel(ModelPart root) {
        this.root = root;
        this.cap = root.getChild("cap");
        this.eyes = root.getChild("eyes");
        this.mass = root.getChild("mass");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Shell over the upper skull (stops above the eye row at y=-4) so the player's own hair doesn't show.
        root.addOrReplaceChild("cap", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -8, -4, 8, 3, 8, new CubeDeformation(0.5f))
                .texOffs(0, 0).addBox(-4, -5, 2.5f, 8, 3, 1.5f, new CubeDeformation(0.5f)), PartPose.ZERO);
        // The hair mass (CX-14c): a solid layer hugging the skull that the clumps grow out of: a slab over the top whose
        // front edge stays above the brows, a thick back and the temples.
        root.addOrReplaceChild("mass", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -8, -4, 8, 1.5f, 8, new CubeDeformation(0.6f))
                .texOffs(0, 0).addBox(-4, -8, 1, 8, 5, 3, new CubeDeformation(0.6f))
                .texOffs(0, 0).addBox(-4, -8, -3, 1, 3.5f, 4, new CubeDeformation(0.6f))
                .texOffs(0, 0).addBox(3, -8, -3, 1, 3.5f, 4, new CubeDeformation(0.6f)), PartPose.ZERO);
        // Pupils: two thin boxes just in front of the skin's inner eye pixels.
        root.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(40, 0)
                        .addBox(-2, -4, -4.06f, 1, 1, 0.01f)
                        .addBox(1, -4, -4.06f, 1, 1, 0.01f),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Follow the wearer's head. */
    public void copyHead(ModelPart head) {
        root.copyFrom(head);
    }

    public void renderCap(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        cap.visible = true;
        eyes.visible = false;
        mass.visible = false;
        root.render(pose, vc, light, overlay, r, g, b, 1f);
    }

    public void renderMass(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        cap.visible = false;
        eyes.visible = false;
        mass.visible = true;
        root.render(pose, vc, light, overlay, r, g, b, 1f);
    }

    public void renderEyes(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        cap.visible = false;
        eyes.visible = true;
        mass.visible = false;
        root.render(pose, vc, light, overlay, r, g, b, 1f);
    }
}
