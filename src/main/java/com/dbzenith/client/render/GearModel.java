package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
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
 * The 3D pieces of the fighting clothes (CX-14e): battle-armour shoulder pads with their trim, a Namekian cape,
 * turban and wide shoulder pads, the hanging tails of a gi's sash, and a Majin's baggy trousers. Greyscale materials
 * from textures/entity/gear_parts.png (cloth, plate, band), tinted per piece.
 */
public class GearModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "gear"), "main");

    public enum Piece { PADS, PAD_TRIM, NAMEK_PADS, CAPE, TURBAN, TURBAN_BAND, SASH_TAILS, BAGGY }

    private final ModelPart head, body, rightArm, leftArm, rightLeg, leftLeg;

    public GearModel(ModelPart root) {
        head = root.getChild("head");
        body = root.getChild("body");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    private static final int CLOTH = 0, PLATE = 32, BAND_V = 16;

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        // Battle armour: rounded plates over the shoulders, tilted outward, with a trim strip along their lower edge.
        rightArm.addOrReplaceChild("pads", CubeListBuilder.create().texOffs(PLATE, 0).addBox(-3.9f, -2.9f, -2.8f, 5.2f, 3.2f, 5.6f),
                PartPose.rotation(0, 0, 0.22f));
        leftArm.addOrReplaceChild("pads", CubeListBuilder.create().texOffs(PLATE, 0).addBox(-1.3f, -2.9f, -2.8f, 5.2f, 3.2f, 5.6f),
                PartPose.rotation(0, 0, -0.22f));
        rightArm.addOrReplaceChild("pad_trim", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-4f, 0.1f, -2.9f, 5.4f, 0.7f, 5.8f),
                PartPose.rotation(0, 0, 0.22f));
        leftArm.addOrReplaceChild("pad_trim", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-1.4f, 0.1f, -2.9f, 5.4f, 0.7f, 5.8f),
                PartPose.rotation(0, 0, -0.22f));

        // Namekian garb: broad, flat, squared pads that jut past the shoulders and hold the cape.
        rightArm.addOrReplaceChild("namek_pads", CubeListBuilder.create().texOffs(PLATE, 0).addBox(-4.8f, -2.9f, -2.7f, 6.2f, 1.8f, 5.4f),
                PartPose.rotation(0, 0, 0.12f));
        leftArm.addOrReplaceChild("namek_pads", CubeListBuilder.create().texOffs(PLATE, 0).addBox(-1.4f, -2.9f, -2.7f, 6.2f, 1.8f, 5.4f),
                PartPose.rotation(0, 0, -0.12f));
        body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-5.2f, 0, 0, 10.4f, 19f, 0.5f),
                PartPose.offset(0, 0.2f, 2.4f));
        head.addOrReplaceChild("turban", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-4, -8.4f, -4, 8, 3.6f, 8, new CubeDeformation(0.7f)),
                PartPose.ZERO);
        head.addOrReplaceChild("turban_band", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-4, -5.6f, -4, 8, 1f, 8, new CubeDeformation(0.8f)),
                PartPose.ZERO);

        // A gi's sash: two short tails hanging from the knot.
        body.addOrReplaceChild("sash_tails", CubeListBuilder.create().texOffs(PLATE, BAND_V)
                .addBox(0.9f, 0, 0, 1.3f, 4.2f, 0.45f).addBox(2.3f, 0, 0, 1.2f, 3.4f, 0.45f), PartPose.offsetAndRotation(0, 10.6f, -2.75f, -0.08f, 0, 0.05f));

        // Majin trousers: billowing out from hip to the ankle cuffs.
        rightLeg.addOrReplaceChild("baggy", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-2, 0, -2, 4, 9.2f, 4, new CubeDeformation(0.85f, 0.05f, 0.85f)),
                PartPose.ZERO);
        leftLeg.addOrReplaceChild("baggy", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-2, 0, -2, 4, 9.2f, 4, new CubeDeformation(0.85f, 0.05f, 0.85f)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void follow(PlayerModel<?> m) {
        head.copyFrom(m.head);
        body.copyFrom(m.body);
        rightArm.copyFrom(m.rightArm);
        leftArm.copyFrom(m.leftArm);
        rightLeg.copyFrom(m.rightLeg);
        leftLeg.copyFrom(m.leftLeg);
    }

    /** Draws one kind of piece (both sides where there are two). {@code capeLift}: 0 hanging .. 1 streaming behind. */
    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay, Piece piece, float capeLift, float r, float g, float b) {
        String name = switch (piece) {
            case PADS -> "pads";
            case PAD_TRIM -> "pad_trim";
            case NAMEK_PADS -> "namek_pads";
            case CAPE -> "cape";
            case TURBAN -> "turban";
            case TURBAN_BAND -> "turban_band";
            case SASH_TAILS -> "sash_tails";
            case BAGGY -> "baggy";
        };
        for (ModelPart parent : new ModelPart[]{head, body, rightArm, leftArm, rightLeg, leftLeg}) {
            if (!parent.hasChild(name)) continue;
            ModelPart p = parent.getChild(name);
            if (piece == Piece.CAPE) p.xRot = 0.06f + 1.1f * capeLift;
            pose.pushPose();
            parent.translateAndRotate(pose);
            p.render(pose, vc, light, overlay, r, g, b, 1f);
            pose.popPose();
        }
    }
}
