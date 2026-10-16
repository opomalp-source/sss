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

import java.util.HashMap;
import java.util.Map;

/**
 * The ape's face grown on the player's head ({@link ApeShape}): brow, muzzle, nose, fangs, a jaw that drops open,
 * cheek tufts and ears. Drawn by {@link ApeLayer} in the head's space, painted by {@code tools/GreatApeGen.java}.
 */
public class ApeHeadModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "great_ape_head"), "main");

    private final ModelPart root;
    private final Map<String, ModelPart> parts = new HashMap<>();

    public ApeHeadModel(ModelPart root) {
        this.root = root;
        for (ApeShape.Part part : ApeShape.PARTS.values()) {
            ModelPart parent = part.parent().isEmpty() ? root : parts.get(part.parent());
            parts.put(part.name(), parent.getChild(part.name()));
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        for (ApeShape.Part part : ApeShape.PARTS.values()) {
            CubeListBuilder cubes = CubeListBuilder.create();
            for (ApeShape.Box b : ApeShape.BOXES) {
                if (!b.part().equals(part.name())) continue;
                cubes.texOffs(b.u(), b.v()).mirror(b.mirror()).addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d(), CubeDeformation.NONE);
            }
            PartDefinition parent = part.parent().isEmpty() ? mesh.getRoot() : defs.get(part.parent());
            defs.put(part.name(), parent.addOrReplaceChild(part.name(), cubes,
                    PartPose.offsetAndRotation(part.px(), part.py(), part.pz(), part.rx(), part.ry(), part.rz())));
        }
        return LayerDefinition.create(mesh, ApeShape.TEX_W, ApeShape.TEX_H);
    }

    /**
     * Poses the face: {@code open} 0 shut .. 1 the jaw dropped wide (a roar), the ears laid back as it opens and
     * flicking now and then.
     */
    public void pose(float open, float age) {
        ModelPart jaw = parts.get("jaw");
        jaw.xRot = 0.95f * open;
        float flick = (float) Math.pow(Math.max(0, Math.sin(age * 0.05f)), 30) * 0.3f;   // an ear twitch now and then
        ApeShape.Part re = ApeShape.PARTS.get("right_ear"), le = ApeShape.PARTS.get("left_ear");
        parts.get("right_ear").zRot = re.rz() + 0.35f * open + flick;
        parts.get("left_ear").zRot = le.rz() - 0.35f * open - flick;
    }

    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay) {
        root.render(pose, vc, light, overlay, 1f, 1f, 1f, 1f);
    }
}
