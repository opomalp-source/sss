package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
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

/**
 * Things NPCs wear in 3D (CX-12): the old master's turtle shell, the officer's cap, the judge's tall hat, a soldier's
 * scouter, a Kai's earrings, a sproutling's bulbous crown and the halo of the dead. Greyscale materials from
 * gear_parts.png (cloth 0,0; plate 32,0; band 32,16), tinted per piece.
 */
public class NpcExtrasModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "npc_extras"), "main");

    /** A piece and the parts it is made of, each with its own colour. */
    public enum Piece {
        SHELL("shell", 0x3E7A3A), SHELL_RIM("shell_rim", 0xE0C060),
        CAP("cap", 0x24346A), CAP_BADGE("cap_badge", 0xE0B040),
        HAT("hat", 0x2A1838), HAT_PLATE("hat_plate", 0xE0B040),
        SCOUTER("scouter", 0x2A2A30), SCOUTER_LENS("scouter_lens", 0x40E070),
        EARRINGS("earrings", 0xF0C040), DOME("dome", 0x5AA83A), HALO("halo", 0xFFE070);

        final String part;
        final int color;

        Piece(String part, int color) {
            this.part = part;
            this.color = color;
        }
    }

    private final ModelPart head, body;

    public NpcExtrasModel(ModelPart root) {
        head = root.getChild("head");
        body = root.getChild("body");
    }

    private static final int CLOTH = 0, PLATE = 32, BAND_V = 16;

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);

        // the turtle shell: a domed back plate in two steps, a pale rim round it
        body.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(PLATE, 0)
                .addBox(-4.4f, 0.6f, 2.1f, 8.8f, 9.8f, 1.6f).addBox(-3.4f, 1.6f, 3.6f, 6.8f, 7.8f, 1.2f).addBox(-2f, 3f, 4.7f, 4f, 5f, 0.8f), PartPose.ZERO);
        body.addOrReplaceChild("shell_rim", CubeListBuilder.create().texOffs(PLATE, BAND_V)
                .addBox(-4.9f, 0.1f, 2.0f, 9.8f, 0.7f, 1.8f).addBox(-4.9f, 10.2f, 2.0f, 9.8f, 0.7f, 1.8f)
                .addBox(-4.9f, 0.8f, 2.0f, 0.7f, 9.4f, 1.8f).addBox(4.2f, 0.8f, 2.0f, 0.7f, 9.4f, 1.8f), PartPose.ZERO);
        // the officer's cap: crown and brim, a badge on the front
        head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(CLOTH, 0)
                .addBox(-4.4f, -9.4f, -4.4f, 8.8f, 2.2f, 8.8f).addBox(-4.2f, -7.4f, -6.6f, 8.4f, 0.5f, 2.4f), PartPose.ZERO);
        head.addOrReplaceChild("cap_badge", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-0.9f, -9.1f, -4.55f, 1.8f, 1.4f, 0.2f), PartPose.ZERO);
        // the judge's hat: tall and square, a gold plate on the front
        head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(CLOTH, 0)
                .addBox(-4.3f, -13.6f, -4.3f, 8.6f, 6.2f, 8.6f).addBox(-4.6f, -8.2f, -4.6f, 9.2f, 1f, 9.2f), PartPose.ZERO);
        head.addOrReplaceChild("hat_plate", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-2.2f, -12.8f, -4.45f, 4.4f, 4f, 0.2f), PartPose.ZERO);
        // a scouter over the left eye: ear piece, arm, lens
        head.addOrReplaceChild("scouter", CubeListBuilder.create().texOffs(PLATE, 0)
                .addBox(3.9f, -5.2f, -1.2f, 0.9f, 2.4f, 2.4f).addBox(4.05f, -6.3f, -4.7f, 0.5f, 0.7f, 4f), PartPose.ZERO);
        head.addOrReplaceChild("scouter_lens", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(1.2f, -6.8f, -4.8f, 3.2f, 2.4f, 0.15f), PartPose.ZERO);
        // a Kai's earrings: a ring and a bead on each ear
        head.addOrReplaceChild("earrings", CubeListBuilder.create().texOffs(PLATE, BAND_V)
                .addBox(-5.2f, -3.2f, -0.6f, 0.5f, 1.2f, 1.2f).addBox(-5.3f, -2f, -0.4f, 0.7f, 0.8f, 0.8f)
                .addBox(4.7f, -3.2f, -0.6f, 0.5f, 1.2f, 1.2f).addBox(4.6f, -2f, -0.4f, 0.7f, 0.8f, 0.8f), PartPose.ZERO);
        // a sproutling's bulbous crown
        head.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(PLATE, 0)
                .addBox(-4.6f, -10.4f, -4.6f, 9.2f, 2.8f, 9.2f).addBox(-3.4f, -11.8f, -3.4f, 6.8f, 1.6f, 6.8f), PartPose.ZERO);
        // the halo of the dead: eight short bars in a ring floating over the head
        PartDefinition halo = head.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offset(0, -11.2f, 0));
        for (int k = 0; k < 8; k++) {
            float a = k * (float) Math.PI / 4;
            halo.addOrReplaceChild("bar" + k, CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-1.35f, -0.3f, -0.3f, 2.7f, 0.6f, 0.6f),
                    PartPose.offsetAndRotation((float) Math.sin(a) * 3.2f, 0, (float) Math.cos(a) * 3.2f, 0, a + (float) Math.PI / 2, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Turns the halo slowly about its axis. */
    public void spinHalo(float angle) {
        head.getChild("halo").yRot = angle;
    }

    public void follow(ModelPart parentHead, ModelPart parentBody) {
        head.copyFrom(parentHead);
        body.copyFrom(parentBody);
    }

    /** Draws one piece in its own colour (or {@code color} if given, -1 for the piece's own). */
    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay, Piece piece, int color) {
        int c = color >= 0 ? color : piece.color;
        for (ModelPart parent : new ModelPart[]{head, body}) {
            if (!parent.hasChild(piece.part)) continue;
            pose.pushPose();
            parent.translateAndRotate(pose);
            parent.getChild(piece.part).render(pose, vc, light, overlay, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f);
            pose.popPose();
        }
    }
}
