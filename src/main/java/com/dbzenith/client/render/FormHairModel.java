package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.transform.Form;
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
import net.minecraft.util.Mth;

/**
 * Transformation and character hair, and eyes, modelled in code: tapered three-tier spikes per style.
 * All styles live in one mesh as separate groups whose visibility is toggled per form.
 * Coordinates are in head space: the player's head cube spans x/z -4..4 and y -8..0.
 */
public class FormHairModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "form_hair"), "main");

    private final ModelPart root;
    private final ModelPart cap;
    private final ModelPart spiky;
    private final ModelPart tall;
    private final ModelPart longHair;
    private final ModelPart slim;
    private final ModelPart eyes;

    public FormHairModel(ModelPart root) {
        this.root = root;
        this.cap = root.getChild("cap");
        this.spiky = root.getChild("spiky");
        this.tall = root.getChild("tall");
        this.longHair = root.getChild("long");
        this.slim = root.getChild("slim");
        this.eyes = root.getChild("eyes");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Shell over the upper skull (stops above the eye row at y=-4) so the player's own hair doesn't show.
        root.addOrReplaceChild("cap", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -8, -4, 8, 3, 8, new CubeDeformation(0.5f))
                .texOffs(0, 0).addBox(-4, -5, 2.5f, 8, 3, 1.5f, new CubeDeformation(0.5f)), PartPose.ZERO);

        // Directions are (outward x, up, outward z); the face is at -z.
        PartDefinition spiky = root.addOrReplaceChild("spiky", CubeListBuilder.create(), PartPose.ZERO);
        spike(spiky, "crown", 0, -8, 0.5f, 3.2f, 7, 0, 1, 0.5f);
        spike(spiky, "crown_l", -2, -8, 0, 2.8f, 6.5f, -0.6f, 1, 0.2f);
        spike(spiky, "crown_r", 2, -8, 0, 2.8f, 6.5f, 0.6f, 1, 0.2f);
        spike(spiky, "crown_bl", -1, -8, 2, 2.8f, 6, -0.3f, 1, 0.8f);
        spike(spiky, "crown_br", 1, -8, 2, 2.8f, 6, 0.3f, 1, 0.8f);
        spike(spiky, "bang_l", -2, -7.5f, -3, 2.4f, 5, -0.4f, 0.8f, -1);
        spike(spiky, "bang_r", 2, -7.5f, -3, 2.4f, 5, 0.4f, 0.8f, -1);
        spike(spiky, "bang_c", 0, -7.8f, -3.4f, 2.2f, 4.5f, 0, 0.6f, -1);
        spike(spiky, "side_l", -4, -6.5f, 0, 2.6f, 5.5f, -1, 0.7f, 0.1f);
        spike(spiky, "side_r", 4, -6.5f, 0, 2.6f, 5.5f, 1, 0.7f, 0.1f);
        spike(spiky, "side_bl", -3.6f, -7.5f, 2, 2.4f, 5, -1, 0.8f, 0.7f);
        spike(spiky, "side_br", 3.6f, -7.5f, 2, 2.4f, 5, 1, 0.8f, 0.7f);
        spike(spiky, "back", 0, -5.5f, 4, 3, 6, 0, 0.4f, 1);
        spike(spiky, "back_l", -2.5f, -4.5f, 4, 2.6f, 5, -0.5f, 0.2f, 1);
        spike(spiky, "back_r", 2.5f, -4.5f, 4, 2.6f, 5, 0.5f, 0.2f, 1);
        spike(spiky, "crown_fl", -1.2f, -8, -1.5f, 2.6f, 6.5f, -0.35f, 1, -0.35f);
        spike(spiky, "crown_fr", 1.2f, -8, -1.5f, 2.6f, 6.5f, 0.35f, 1, -0.35f);
        spike(spiky, "bang_ll", -3.4f, -7, -3, 2, 4, -0.8f, 0.4f, -1);       // fringe framing the face
        spike(spiky, "bang_rr", 3.4f, -7, -3, 2, 4, 0.8f, 0.4f, -1);
        spike(spiky, "nape_l", -3.2f, -3.5f, 3.6f, 2.2f, 4, -0.7f, -0.2f, 1);
        spike(spiky, "nape_r", 3.2f, -3.5f, 3.6f, 2.2f, 4, 0.7f, -0.2f, 1);

        PartDefinition tall = root.addOrReplaceChild("tall", CubeListBuilder.create(), PartPose.ZERO);
        spike(tall, "crown", 0, -8, 0.5f, 3.4f, 10, 0, 1, 0.25f);
        spike(tall, "crown_l", -2, -8, 0, 3, 9, -0.35f, 1, 0.1f);
        spike(tall, "crown_r", 2, -8, 0, 3, 9, 0.35f, 1, 0.1f);
        spike(tall, "crown_bl", -1.2f, -8, 2, 3, 8.5f, -0.2f, 1, 0.5f);
        spike(tall, "crown_br", 1.2f, -8, 2, 3, 8.5f, 0.2f, 1, 0.5f);
        spike(tall, "front_l", -2.2f, -7.8f, -2.8f, 2.6f, 7, -0.3f, 1, -0.4f);
        spike(tall, "front_r", 2.2f, -7.8f, -2.8f, 2.6f, 7, 0.3f, 1, -0.4f);
        spike(tall, "side_l", -4, -7, 0.5f, 2.6f, 7, -0.8f, 1, 0.2f);
        spike(tall, "side_r", 4, -7, 0.5f, 2.6f, 7, 0.8f, 1, 0.2f);
        spike(tall, "back", 0, -6, 4, 3, 7, 0, 0.6f, 1);
        spike(tall, "crown_fl", -1, -8, -1.6f, 2.8f, 9, -0.2f, 1, -0.2f);
        spike(tall, "crown_fr", 1, -8, -1.6f, 2.8f, 9, 0.2f, 1, -0.2f);
        spike(tall, "back_l", -2.6f, -5, 4, 2.6f, 6, -0.5f, 0.5f, 1);
        spike(tall, "back_r", 2.6f, -5, 4, 2.6f, 6, 0.5f, 0.5f, 1);
        spike(tall, "bang", -0.8f, -7.2f, -4.3f, 1.4f, 4.5f, 0.15f, -1, -0.25f); // single bang hanging over the forehead

        PartDefinition longHair = root.addOrReplaceChild("long", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-5, -6, 3.5f, 10, 26, 2, new CubeDeformation(0.2f))
                .texOffs(0, 16).addBox(-5.5f, -6, -1, 1.5f, 10, 4.5f)
                .texOffs(0, 16).addBox(4, -6, -1, 1.5f, 10, 4.5f), PartPose.ZERO);
        spike(longHair, "crown", 0, -8, 0.5f, 3.2f, 5, 0, 1, 0.6f);
        spike(longHair, "crown_l", -2, -8, 0, 2.8f, 4.5f, -0.6f, 1, 0.4f);
        spike(longHair, "crown_r", 2, -8, 0, 2.8f, 4.5f, 0.6f, 1, 0.4f);
        spike(longHair, "bang", -0.8f, -7.2f, -4.3f, 1.4f, 4.5f, 0.15f, -1, -0.25f);
        spike(longHair, "tip_l", -3, 18, 4.5f, 3, 5, -0.2f, -1, 0.3f);
        spike(longHair, "tip_r", 3, 18, 4.5f, 3, 5, 0.2f, -1, 0.3f);

        PartDefinition slim = root.addOrReplaceChild("slim", CubeListBuilder.create(), PartPose.ZERO);
        spike(slim, "crown", 0, -8, 1, 2.6f, 5.5f, 0, 0.8f, 1);
        spike(slim, "crown_l", -2, -8, 0.5f, 2.4f, 5, -0.4f, 0.8f, 1);
        spike(slim, "crown_r", 2, -8, 0.5f, 2.4f, 5, 0.4f, 0.8f, 1);
        spike(slim, "front_l", -2, -7.8f, -2.5f, 2.2f, 4, -0.3f, 1, 0.6f);
        spike(slim, "front_r", 2, -7.8f, -2.5f, 2.2f, 4, 0.3f, 1, 0.6f);
        spike(slim, "back", 0, -6, 3.8f, 2.6f, 5, 0, 0.3f, 1);
        spike(slim, "side_l", -3.8f, -6.5f, 0.5f, 2, 4, -1, 0.6f, 0.4f);
        spike(slim, "side_r", 3.8f, -6.5f, 0.5f, 2, 4, 1, 0.6f, 0.4f);

        // Pupils: two thin boxes just in front of the default skin's inner eye pixels.
        root.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(40, 0)
                        .addBox(-2, -4, -4.06f, 1, 1, 0.01f)
                        .addBox(1, -4, -4.06f, 1, 1, 0.01f),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * A tapered spike (three stacked boxes, each narrower) rooted at (x, y, z), pointing along (dx, up, dz).
     * The default model "up" is -y; rotation order is Z*X, so xRot = asin(-dz) and zRot = atan2(dx, up).
     */
    private static void spike(PartDefinition parent, String name, float x, float y, float z, float width, float height,
                              float dx, float up, float dz) {
        float len = Mth.sqrt(dx * dx + up * up + dz * dz);
        float nx = dx / len;
        float nu = up / len;
        float nz = dz / len;
        float xRot = (float) Math.asin(-nz);
        float zRot = (float) Math.atan2(nx, nu);
        float w2 = width * 0.62f;
        float w3 = width * 0.3f;
        // Each tier has its own texture region: dark roots, mid-tone, bright tips (see tools/ArtGen.java).
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(0, 48).addBox(-width / 2, -height * 0.45f, -width / 2, width, height * 0.45f, width)
                        .texOffs(16, 48).addBox(-w2 / 2, -height * 0.8f, -w2 / 2, w2, height * 0.35f, w2)
                        .texOffs(32, 48).addBox(-w3 / 2, -height, -w3 / 2, w3, height * 0.2f, w3),
                PartPose.offsetAndRotation(x, y, z, xRot, 0, zRot));
    }

    /** Follow the wearer's head. */
    public void copyHead(ModelPart head) {
        root.copyFrom(head);
    }

    public void renderHair(PoseStack pose, VertexConsumer vc, int light, int overlay, Form.HairStyle style, float r, float g, float b) {
        cap.visible = true;
        spiky.visible = style == Form.HairStyle.SPIKY;
        tall.visible = style == Form.HairStyle.SPIKY_TALL;
        longHair.visible = style == Form.HairStyle.LONG;
        slim.visible = style == Form.HairStyle.SLIM;
        eyes.visible = false;
        root.render(pose, vc, light, overlay, r, g, b, 1f);
    }

    public void renderEyes(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        cap.visible = spiky.visible = tall.visible = longHair.visible = slim.visible = false;
        eyes.visible = true;
        root.render(pose, vc, light, overlay, r, g, b, 1f);
    }
}
