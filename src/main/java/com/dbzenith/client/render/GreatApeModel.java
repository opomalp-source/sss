package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
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
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * The Great Ape (rebuilt in the Great Ape rework): a hunched, massive ape from {@link ApeShape}, its fur, bare face,
 * chest and hands painted by {@code tools/GreatApeGen.java} (brown, gold for the golden ape, a greener gold for the
 * legendary one), its eyes glowing red in their own pass.
 * <p>It moves like a beast: it breathes, its jaw works, its tail sways in a curl; walking, it stomps, rolling side to
 * side with its long arms swinging; a blow is an overhead swipe and a smash; in the air its arms go up. When the change
 * takes it ({@link #setRoar}) it throws its head back and roars with its arms spread, then pounds its chest.
 */
public class GreatApeModel extends EntityModel<Player> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DBZenith.MOD_ID, "great_ape"), "main");
    public static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/great_ape.png");
    private static final ResourceLocation GOLDEN = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/great_ape_golden.png");
    private static final ResourceLocation LEGENDARY = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/great_ape_legendary.png");
    public static final ResourceLocation EYES = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/great_ape_eyes.png");

    /** The fur for an ape form: brown under the moon, gold for the golden ape, a green-gold for the legendary one. */
    public static ResourceLocation texture(String form) {
        return "golden_ape".equals(form) ? GOLDEN : "legendary_great_ape".equals(form) ? LEGENDARY : TEXTURE;
    }

    private final ModelPart root;
    private final Map<String, ModelPart> parts = new HashMap<>();
    private float roar = -1, airborne;

    public GreatApeModel(ModelPart root) {
        this.root = root;
        collect(root);
    }

    private void collect(ModelPart p) {
        for (String name : ApeShape.PARTS.keySet()) {
            ApeShape.Part part = ApeShape.PARTS.get(name);
            ModelPart parent = part.parent().isEmpty() ? root : parts.get(part.parent());
            if (parent != null && !parts.containsKey(name)) parts.put(name, parent.getChild(name));
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

    /** How far into the roar that comes with the change (0..1), or -1 for none. */
    public void setRoar(float t) {
        roar = t;
    }

    /** 0 on the ground .. 1 well up in the air. */
    public void setAirborne(float k) {
        airborne = k;
    }

    private ModelPart p(String name) {
        return parts.get(name);
    }

    private void rest() {
        for (ApeShape.Part part : ApeShape.PARTS.values()) {
            ModelPart m = parts.get(part.name());
            m.setPos(part.px(), part.py(), part.pz());
            m.setRotation(part.rx(), part.ry(), part.rz());
        }
    }

    @Override
    public void setupAnim(Player player, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        rest();
        ModelPart pelvis = p("pelvis"), torso = p("torso"), head = p("head"), jaw = p("jaw");
        ModelPart ra = p("right_arm"), la = p("left_arm"), rf = p("right_forearm"), lf = p("left_forearm");
        ModelPart rl = p("right_leg"), ll = p("left_leg"), rs = p("right_shin"), ls = p("left_shin");

        float breath = Mth.sin(age * 0.08f);                                     // breathing: the chest heaves
        torso.xRot += breath * 0.02f;
        ra.zRot += breath * 0.03f;
        la.zRot -= breath * 0.03f;
        jaw.xRot = 0.05f + 0.12f * (float) Math.pow(Math.max(0, Mth.sin(age * 0.045f)), 8);   // now and then a snarl

        // walking: heavy stomps, rolling side to side, the long arms swinging
        float w = limbSwing * 0.42f, amp = Math.min(1f, limbSwingAmount * 1.3f);
        float c = Mth.cos(w), s = Mth.sin(w);
        rl.xRot += c * 0.7f * amp;
        ll.xRot -= c * 0.7f * amp;
        rs.xRot += Math.max(0, -s) * 0.8f * amp;
        ls.xRot += Math.max(0, s) * 0.8f * amp;
        ra.xRot -= c * 0.55f * amp;
        la.xRot += c * 0.55f * amp;
        rf.xRot -= Math.max(0, c) * 0.4f * amp;
        lf.xRot -= Math.max(0, -c) * 0.4f * amp;
        torso.zRot += s * 0.09f * amp;
        torso.yRot += c * 0.08f * amp;
        pelvis.y += Math.abs(s) * 0.9f * amp;
        head.zRot -= s * 0.06f * amp;

        // looking about: the head turns, the body follows a little
        float yaw = Mth.clamp(netHeadYaw, -70, 70) * Mth.DEG_TO_RAD;
        head.yRot += yaw * 0.75f;
        torso.yRot += yaw * 0.2f;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.7f;

        // in the air: arms up and out, legs drawn up
        if (airborne > 0) {
            ra.xRot -= 1.2f * airborne;
            la.xRot -= 1.2f * airborne;
            ra.zRot += 0.5f * airborne;
            la.zRot -= 0.5f * airborne;
            rl.xRot -= 0.4f * airborne;
            ll.xRot -= 0.4f * airborne;
        }

        // a blow: one arm swung up over the head and brought smashing down, the body turning into it
        if (attackTime > 0) {
            float a = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
            float down = attackTime > 0.5f ? (attackTime - 0.5f) * 2 : 0;
            ra.xRot -= a * 2.4f - down * 0.8f;
            rf.xRot -= a * 0.7f;
            ra.zRot -= a * 0.25f;
            torso.yRot -= a * 0.35f;
            torso.xRot += down * 0.25f;
            jaw.xRot = Math.max(jaw.xRot, a * 0.45f);
        }

        // the change: head thrown back in a roar, arms spread, then fists on the chest
        if (roar >= 0 && roar < 1) {
            float in = Mth.clamp(roar / 0.08f, 0, 1), out = Mth.clamp((1 - roar) / 0.1f, 0, 1), k = in * out;
            if (roar < 0.5f) {
                float r = Mth.clamp((roar - 0.05f) / 0.4f, 0, 1);
                torso.xRot -= 0.38f * k;                                         // standing tall
                head.xRot -= 0.75f * k;
                jaw.xRot = Math.max(jaw.xRot, 0.75f * k * (0.85f + 0.15f * Mth.sin(age * 1.7f)));
                ra.zRot += 1.0f * k;
                la.zRot -= 1.0f * k;
                ra.xRot -= 0.5f * k;
                la.xRot -= 0.5f * k;
                rf.xRot -= 0.6f * k * r;
                lf.xRot -= 0.6f * k * r;
                head.zRot += 0.05f * Mth.sin(age * 2.3f) * k;                   // shaking with it
            } else {
                float beat = (roar - 0.5f) * 14;                                 // fists on the chest, one and the other
                float rb = Math.max(0, Mth.sin(beat * Mth.PI)), lb = Math.max(0, -Mth.sin(beat * Mth.PI));
                torso.xRot -= 0.3f * k;
                head.xRot -= 0.3f * k;
                jaw.xRot = Math.max(jaw.xRot, 0.4f * k);
                ra.xRot -= (0.55f + 0.35f * rb) * k;                            // elbows out, fists folded in onto the chest
                la.xRot -= (0.55f + 0.35f * lb) * k;
                ra.zRot -= (0.55f + 0.15f * rb) * k;
                la.zRot += (0.55f + 0.15f * lb) * k;
                ra.yRot += 0.5f * k;
                la.yRot -= 0.5f * k;
                rf.xRot -= (2.0f - 0.25f * rb) * k;
                lf.xRot -= (2.0f - 0.25f * lb) * k;
            }
        }

        // the tail: a slow sway rolling down its length, curling up at the end
        float tw = age * 0.07f;
        p("tail").yRot = Mth.sin(tw) * 0.3f;
        p("tail2").yRot = Mth.sin(tw - 0.7f) * 0.3f;
        p("tail3").yRot = Mth.sin(tw - 1.4f) * 0.35f;
        p("tail4").yRot = Mth.sin(tw - 2.1f) * 0.4f;
        p("tail3").xRot += Mth.sin(tw * 0.7f) * 0.1f;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vc, light, overlay, r, g, b, a);
    }
}
