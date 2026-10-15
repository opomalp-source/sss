package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The 3D pieces of the fighting clothes (CX-14e; reworked in the clothing rework so things stick out):
 * <ul>
 *   <li>Saiyan battle armour's shoulder pads, layered: a plate over the shoulder, a lip flaring down and out, a trim;</li>
 *   <li>the Frieza Force armour's pads, sweeping up and out like wings, in two tiers;</li>
 *   <li>a Namekian's cape and wide square pads, a gi's sash tails, a hoodie's hood, a Majin's baggy trousers;</li>
 *   <li>head clothing ({@code HeadwearItem}): Piccolo's domed turban and its band, Pan's bandana and its knot, a
 *       headband and its tails;</li>
 * </ul>
 * Greyscale materials from textures/entity/gear_parts.png (cloth, plate, band), tinted per piece. (The thick parts of the
 * outfits, plates, gloves and boots, are drawn by {@code GearLayer} on the figure itself.)
 */
public class GearModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "gear"), "main");

    public enum Piece {
        PADS("pads"), PAD_LIP("pad_lip"), PAD_TRIM("pad_trim"), FROST_PADS("frost_pads"), FROST_PAD_TRIM("frost_pad_trim"),
        NAMEK_PADS("namek_pads"), CAPE("cape"), SASH_TAILS("sash_tails"), BAGGY("baggy"), HOOD("hood"),
        TURBAN("turban"), TURBAN_BAND("turban_band"), BANDANA("bandana"), BANDANA_KNOT("bandana_knot"),
        HEADBAND("headband"), HEADBAND_TAILS("headband_tails");

        final String part;

        Piece(String part) {
            this.part = part;
        }
    }

    private final ModelPart head, body, rightArm, leftArm, rightLeg, leftLeg;

    public GearModel(ModelPart root) {
        head = root.getChild("head");
        body = root.getChild("body");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    private static final int CLOTH = 0, PLATE = 32, BAND_V = 16, CAPE_PANELS = 4;

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        // Saiyan battle armour: a plate over each shoulder, a lip flaring down and out at its edge, a trim along the lip
        pads(rightArm, -1, 0.3f);
        pads(leftArm, 1, -0.3f);

        // the Frieza Force's armour: big pads sweeping up and out like wings, a second smaller tier on top
        for (int side = -1; side <= 1; side += 2) {
            PartDefinition arm = side < 0 ? rightArm : leftArm;
            float x0 = side < 0 ? -5.6f : -0.6f;
            PartDefinition wing = arm.addOrReplaceChild("frost_pads", CubeListBuilder.create().texOffs(PLATE, 0)
                    .addBox(x0, -3.3f, -3.2f, 6.2f, 1.5f, 6.4f), PartPose.offsetAndRotation(0, 0.6f, 0, 0, 0, -side * 0.4f));
            wing.addOrReplaceChild("frost_pads_2", CubeListBuilder.create().texOffs(PLATE, 0)
                    .addBox(side < 0 ? -5.1f : -0.1f, -4.3f, -2.6f, 4.6f, 1.1f, 5.2f), PartPose.ZERO);
            arm.addOrReplaceChild("frost_pad_trim", CubeListBuilder.create().texOffs(PLATE, BAND_V)
                    .addBox(side < 0 ? -5.8f : 4.0f, -3.4f, -3.3f, 1.4f, 1.7f, 6.6f), PartPose.offsetAndRotation(0, 0.6f, 0, 0, 0, -side * 0.4f));
        }

        // Namekian garb: broad, flat, squared pads that jut past the shoulders and hold the cape
        rightArm.addOrReplaceChild("namek_pads", CubeListBuilder.create().texOffs(PLATE, 0).addBox(-4.8f, -2.9f, -2.7f, 6.2f, 1.8f, 5.4f),
                PartPose.rotation(0, 0, 0.12f));
        leftArm.addOrReplaceChild("namek_pads", CubeListBuilder.create().texOffs(PLATE, 0).addBox(-1.4f, -2.9f, -2.7f, 6.2f, 1.8f, 5.4f),
                PartPose.rotation(0, 0, -0.12f));
        // the cape (CX-44): four panels hanging one from the next, so it can ripple
        PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-5.2f, 0, 0, 10.4f, 4.75f, 0.5f),
                PartPose.offset(0, 0.2f, 2.4f));
        for (int i = 1; i < CAPE_PANELS; i++)
            cape = cape.addOrReplaceChild("cape_" + i, CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-5.2f, 0, 0, 10.4f, 4.75f, 0.5f),
                    PartPose.offset(0, 4.75f, 0));

        // Piccolo's turban: a band round the brow, a full dome rising over it in rounded tiers
        PartDefinition turban = head.addOrReplaceChild("turban", CubeListBuilder.create()
                .texOffs(CLOTH, 0).addBox(-4.5f, -9.4f, -4.5f, 9, 3.6f, 9, new CubeDeformation(0.25f))
                .texOffs(CLOTH, 0).addBox(-3.8f, -10.6f, -3.8f, 7.6f, 1.4f, 7.6f, new CubeDeformation(0.1f))
                .texOffs(CLOTH, 0).addBox(-2.6f, -11.3f, -2.6f, 5.2f, 0.8f, 5.2f), PartPose.ZERO);
        turban.addOrReplaceChild("turban_fold", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-0.6f, -10.9f, -4.95f, 1.2f, 5.0f, 0.5f),
                PartPose.ZERO);                                                        // the fold up the front
        head.addOrReplaceChild("turban_band", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-4.6f, -6.2f, -4.6f, 9.2f, 1.6f, 9.2f,
                new CubeDeformation(0.2f)), PartPose.ZERO);

        // Pan's bandana: over the top of the head, knotted at the back, two short ends
        head.addOrReplaceChild("bandana", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-4, -8.6f, -4, 8, 3.2f, 8, new CubeDeformation(0.32f))
                .texOffs(CLOTH, 0).addBox(-3.3f, -9.35f, -3.3f, 6.6f, 0.8f, 6.6f),
                PartPose.ZERO);
        PartDefinition knot = head.addOrReplaceChild("bandana_knot", CubeListBuilder.create().texOffs(PLATE, BAND_V)
                .addBox(-1.1f, -0.9f, 0, 2.2f, 1.8f, 1.1f)
                .texOffs(PLATE, BAND_V).addBox(-4, -0.4f, -8.25f, 8, 0.9f, 8, new CubeDeformation(0.4f)), PartPose.offset(0, -5.6f, 4.25f));
        knot.addOrReplaceChild("bandana_end_a", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-0.6f, 0, 0, 1.2f, 2.6f, 0.4f),
                PartPose.offsetAndRotation(-0.5f, 0.6f, 0.7f, 0.25f, 0, 0.3f));
        knot.addOrReplaceChild("bandana_end_b", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-0.6f, 0, 0, 1.2f, 2.2f, 0.4f),
                PartPose.offsetAndRotation(0.5f, 0.6f, 0.7f, 0.2f, 0, -0.25f));

        // a fighter's headband: round the brow, its two tails hanging at the back (they swing, CX-44 style)
        head.addOrReplaceChild("headband", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-4, -6.9f, -4, 8, 1.3f, 8, new CubeDeformation(0.33f)),
                PartPose.ZERO);
        PartDefinition tails = head.addOrReplaceChild("headband_tails", CubeListBuilder.create().texOffs(PLATE, BAND_V)
                .addBox(-0.8f, -0.7f, 0, 1.6f, 1.4f, 0.8f), PartPose.offset(0, -6.25f, 4.3f));
        tails.addOrReplaceChild("tail_a", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-0.55f, 0, 0, 1.1f, 2.4f, 0.3f), PartPose.offset(-0.45f, 0.3f, 0.5f))
                .addOrReplaceChild("tail_a1", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-0.55f, 0, 0, 1.1f, 2.4f, 0.3f), PartPose.offset(0, 2.4f, 0));
        tails.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-0.55f, 0, 0, 1.1f, 2.0f, 0.3f), PartPose.offset(0.55f, 0.3f, 0.5f))
                .addOrReplaceChild("tail_b1", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-0.55f, 0, 0, 1.1f, 2.0f, 0.3f), PartPose.offset(0, 2.0f, 0));

        // A gi's sash: two short tails hanging from the knot, each in two pieces (CX-44)
        PartDefinition sash = body.addOrReplaceChild("sash_tails", CubeListBuilder.create(), PartPose.offsetAndRotation(0, 10.6f, -2.75f, -0.08f, 0, 0.05f));
        sash.addOrReplaceChild("sash_a", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-0.65f, 0, 0, 1.3f, 2.1f, 0.45f), PartPose.offset(1.55f, 0, 0))
                .addOrReplaceChild("sash_a1", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-0.65f, 0, 0, 1.3f, 2.1f, 0.45f), PartPose.offset(0, 2.1f, 0));
        sash.addOrReplaceChild("sash_b", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-0.6f, 0, 0, 1.2f, 1.7f, 0.45f), PartPose.offset(2.9f, 0, 0))
                .addOrReplaceChild("sash_b1", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(-0.6f, 0, 0, 1.2f, 1.7f, 0.45f), PartPose.offset(0, 1.7f, 0));

        // A hoodie's hood, down, lying on the back of the neck.
        body.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-3.6f, -2.4f, 0, 7.2f, 3.6f, 2.2f),
                PartPose.offsetAndRotation(0, 0.4f, 2.0f, 0.32f, 0, 0));

        // Majin trousers: billowing out from hip to the ankle cuffs.
        rightLeg.addOrReplaceChild("baggy", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-2, 0, -2, 4, 9.2f, 4, new CubeDeformation(0.85f, 0.05f, 0.85f)),
                PartPose.ZERO);
        leftLeg.addOrReplaceChild("baggy", CubeListBuilder.create().texOffs(CLOTH, 0).addBox(-2, 0, -2, 4, 9.2f, 4, new CubeDeformation(0.85f, 0.05f, 0.85f)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One side's Saiyan pad: {@code side} -1 right, 1 left; tilted out by {@code tilt}. */
    private static void pads(PartDefinition arm, int side, float tilt) {
        float x0 = side < 0 ? -4.4f : -1.8f;
        arm.addOrReplaceChild("pads", CubeListBuilder.create().texOffs(PLATE, 0).addBox(x0, -3.1f, -3.0f, 6.2f, 1.6f, 6.0f),
                PartPose.rotation(0, 0, tilt));
        float lx = side < 0 ? -4.9f : 3.7f;                                            // the lip at the outer edge, flaring down
        arm.addOrReplaceChild("pad_lip", CubeListBuilder.create().texOffs(PLATE, 0).addBox(lx, -2.4f, -3.0f, 1.2f, 2.8f, 6.0f),
                PartPose.rotation(0, 0, tilt + side * 0.12f));
        arm.addOrReplaceChild("pad_trim", CubeListBuilder.create().texOffs(PLATE, BAND_V).addBox(lx - (side < 0 ? 0.15f : -0.15f), 0.3f, -3.1f, 1.3f, 0.6f, 6.2f),
                PartPose.rotation(0, 0, tilt + side * 0.12f));
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
        render(pose, vc, light, overlay, piece, capeLift, null, r, g, b);
    }

    /** As above; with {@code cloth} (a cape's or a sash's chain, CX-44) the piece swings with it instead. */
    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay, Piece piece, float capeLift, Cloth.Chain cloth, float r, float g, float b) {
        String name = piece.part;
        for (ModelPart parent : new ModelPart[]{head, body, rightArm, leftArm, rightLeg, leftLeg}) {
            if (!parent.hasChild(name)) continue;
            ModelPart p = parent.getChild(name);
            if (piece == Piece.CAPE) {
                if (cloth == null) p.xRot = 0.06f + 1.1f * capeLift;
                else {                                                         // each panel swings from the one above it
                    ModelPart panel = p;
                    for (int i = 0; i < CAPE_PANELS; i++) {
                        int s = Math.min(i, cloth.segments() - 1);
                        panel.xRot = Math.max(i == 0 ? 0.04f : -0.5f, cloth.relPitch(s));   // never into the back
                        panel.zRot = cloth.relRoll(s) * 0.45f;
                        if (i + 1 < CAPE_PANELS) panel = panel.getChild("cape_" + (i + 1));
                    }
                }
            }
            if (piece == Piece.SASH_TAILS && cloth != null) {                  // tied at the front: they swing forward and sideways, but lie against the legs going forward
                for (String[] tail : new String[][]{{"sash_a", "sash_a1"}, {"sash_b", "sash_b1"}}) {
                    float lag = tail[0].equals("sash_b") ? 0.8f : 1f;
                    ModelPart a = p.getChild(tail[0]), a1 = a.getChild(tail[1]);
                    a.xRot = Mth.clamp(cloth.pitch(0) * 0.8f * lag, -1.1f, 0.15f);   // stopping throws them forward
                    a.zRot = cloth.roll(0) * lag;
                    a1.xRot = Mth.clamp(cloth.relPitch(Math.min(1, cloth.segments() - 1)) * 0.8f, -0.8f, 0.3f);
                    a1.zRot = cloth.relRoll(Math.min(1, cloth.segments() - 1));
                }
            }
            if (piece == Piece.HEADBAND_TAILS && cloth != null) {              // tied at the back: they stream behind, never into the head
                for (String[] tail : new String[][]{{"tail_a", "tail_a1"}, {"tail_b", "tail_b1"}}) {
                    float lag = tail[0].equals("tail_b") ? 0.85f : 1f;
                    ModelPart a = p.getChild(tail[0]), a1 = a.getChild(tail[1]);
                    a.xRot = Mth.clamp(cloth.pitch(0) * lag + 0.15f, 0.1f, 1.3f);
                    a.zRot = cloth.roll(0) * lag;
                    a1.xRot = Mth.clamp(cloth.relPitch(Math.min(1, cloth.segments() - 1)), -0.2f, 0.8f);
                    a1.zRot = cloth.relRoll(Math.min(1, cloth.segments() - 1));
                }
            }
            pose.pushPose();
            parent.translateAndRotate(pose);
            p.render(pose, vc, light, overlay, r, g, b, 1f);
            pose.popPose();
        }
    }
}
