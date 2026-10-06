package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Anime proportions (CX-14, user request: "the chest is a little bigger than the torso"): a chest block over the upper
 * half of the torso, wider and deeper than the waist, in three sizes for the lean, athletic and bulky builds. It is a
 * child of the player model's body, so every layer that draws the body (race skins, generated bodies, outfits, fur,
 * glows) draws the chest with the same texture, and it follows every animation. It maps the upper half of the body's
 * texture, so the pecs sit on it and the abs run on below.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class BodyShape {
    static final String[] NAMES = {"dbz_chest_lean", "dbz_chest_athletic", "dbz_chest_bulky"};
    /** How far each build's chest stands out from the body: sideways, up and down, front and back (pixels). */
    static final float[][] INFLATE = {{0.3f, 0.15f, 0.45f}, {0.55f, 0.25f, 0.8f}, {0.85f, 0.35f, 1.15f}};

    private BodyShape() {}

    /** Adds the chest blocks to a player model's body (once). */
    public static void attach(PlayerModel<?> model) {
        Map<String, ModelPart> children = childrenOf(model.body);
        if (children == null || children.containsKey(NAMES[0])) return;
        for (int i = 0; i < NAMES.length; i++) {
            ModelPart chest = bake(INFLATE[i]);
            chest.visible = false;
            children.put(NAMES[i], chest);
        }
    }

    static ModelPart bake(float[] d) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("chest", CubeListBuilder.create().texOffs(16, 16)
                .addBox(-4, 0, -2, 8, 6, 4, new CubeDeformation(d[0], d[1], d[2])), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("chest");
    }

    private static Field childrenField;
    private static boolean failed;

    @SuppressWarnings("unchecked")
    static Map<String, ModelPart> childrenOf(ModelPart part) {
        if (failed) return null;
        try {
            if (childrenField == null) {
                for (Field f : ModelPart.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        childrenField = f;
                        break;
                    }
                }
            }
            if (childrenField != null) return (Map<String, ModelPart>) childrenField.get(part);
            failed = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            DBZenith.LOGGER.warn("Could not add the chest shape to the player model", e);
            failed = true;
        }
        return null;
    }

    /** The build whose chest a player shows, or -1 for none (players drawn with their own Minecraft skin). */
    public static int build(PublicStatePacket state) {
        if (state == null) return -1;
        boolean modLook = BodySkinLayer.active(state) || (state.raceLook() && RaceSkinLayer.texture(state) != null);
        if (!modLook || ArtStyle.get() == ArtStyle.CLASSIC) return -1;
        return Math.max(0, Math.min(NAMES.length - 1, state.bodyType()));
    }

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre event) {
        float turn = com.dbzenith.client.fx.CameraFx.devTurn;
        if (turn != 0 && event.getEntity() == net.minecraft.client.Minecraft.getInstance().player) {
            event.getEntity().yBodyRot = event.getEntity().yBodyRotO = event.getEntity().getYRot() + turn;
        }
        PlayerModel<?> model = event.getRenderer().getModel();
        PublicStatePacket state = ClientPublicStates.get(event.getEntity().getId());
        int build = build(state);
        float bulk = build >= 0 ? FormShape.bulk(state.form()) : 0;
        shape(model, bulk);
        if (bulk >= 1) build = NAMES.length - 1;                                      // heavy forms wear the biggest chest
        Map<String, ModelPart> children = childrenOf(model.body);
        if (children == null) return;
        for (int i = 0; i < NAMES.length; i++) {
            ModelPart chest = children.get(NAMES[i]);
            if (chest != null) chest.visible = i == build;
        }
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event) {
        shape(event.getRenderer().getModel(), 0);                                   // the model is shared: back to normal
    }

    /** Swells (or pares down) the torso and limbs for a form's bulk: wider and deeper body, thicker arms, sturdier legs. */
    static void shape(PlayerModel<?> m, float bulk) {
        float body = 1 + 0.1f * bulk, depth = 1 + 0.14f * bulk, arm = 1 + 0.2f * bulk, leg = 1 + 0.09f * bulk;
        scale(m.body, body, depth);
        scale(m.jacket, body, depth);
        for (ModelPart p : new ModelPart[]{m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve}) scale(p, arm, arm);
        for (ModelPart p : new ModelPart[]{m.rightLeg, m.leftLeg, m.rightPants, m.leftPants}) scale(p, leg, leg);
    }

    private static void scale(ModelPart p, float xz, float z) {
        p.xScale = xz;
        p.zScale = z;
    }
}
