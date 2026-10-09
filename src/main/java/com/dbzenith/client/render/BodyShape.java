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
 * Anime proportions for players and NPCs (CX-14, reworked in CX-22 at the user's request: "the chest is kinda too big
 * for the belly and the arms and legs are kinda skinny").
 * <ul>
 *   <li><b>The torso tapers</b>: a chest block over its upper half and a slimmer waist block over its lower half, so
 *       the chest is fuller than the waist without standing off it like a box, in three builds (lean, athletic,
 *       bulky). Both are children of the body, so every layer that draws the body draws them with its texture, and
 *       they follow every animation; each maps its half of the body texture (pecs above, abs below).</li>
 *   <li><b>Fuller limbs</b>: the arms and legs are thicker than vanilla's sticks, more for heavier builds.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class BodyShape {
    public static final int LEAN = 0, ATHLETIC = 1, BULKY = 2;
    static final String[] NAMES = {"dbz_chest_lean", "dbz_chest_athletic", "dbz_chest_bulky"};
    static final String[] WAISTS = {"dbz_waist_lean", "dbz_waist_athletic", "dbz_waist_bulky"};
    static final String BELLY = "dbz_belly";
    /** How far each build's chest and waist stand out from the body: sideways, up and down, front and back (pixels). */
    static final float[][] CHEST = {{0.22f, 0.1f, 0.3f}, {0.38f, 0.14f, 0.45f}, {0.62f, 0.2f, 0.7f}};
    static final float[][] WAIST = {{0.06f, 0f, 0.12f}, {0.16f, 0.02f, 0.22f}, {0.38f, 0.06f, 0.48f}};
    /** Each build's body width and depth, arm thickness, and leg width and depth (scales over vanilla's; the legs mostly gain
     * depth, so they never merge into one block from the front). */
    static final float[][] LIMBS = {{1.02f, 1.02f, 1.18f, 1.04f, 1.12f}, {1.05f, 1.06f, 1.28f, 1.08f, 1.2f}, {1.1f, 1.12f, 1.4f, 1.14f, 1.3f}};

    private BodyShape() {}

    /** Adds the chest and waist blocks to a player-shaped model's body (once). */
    public static void attach(PlayerModel<?> model) {
        Map<String, ModelPart> children = childrenOf(model.body);
        if (children == null || children.containsKey(NAMES[0])) return;
        for (int i = 0; i < NAMES.length; i++) {
            ModelPart chest = bake(CHEST[i], 16, 16, 0);
            chest.visible = false;
            children.put(NAMES[i], chest);
            ModelPart waist = bake(WAIST[i], 16, 22, 6);
            waist.visible = false;
            children.put(WAISTS[i], waist);
        }
        MeshDefinition mesh = new MeshDefinition();                              // a botched fusion's pot belly (12c)
        mesh.getRoot().addOrReplaceChild("belly", CubeListBuilder.create().texOffs(15, 20)
                .addBox(-4, 6, -3, 8, 6, 5, new CubeDeformation(0.9f, 0.4f, 0.9f)), PartPose.ZERO);
        ModelPart belly = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("belly");
        belly.visible = false;
        children.put(BELLY, belly);
    }

    /** A half-torso block: 8 x 6 x 4 from {@code y}, mapping the body texture from ({@code u}, {@code v}). */
    static ModelPart bake(float[] d, int u, int v, int y) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(u, v)
                .addBox(-4, y, -2, 8, 6, 4, new CubeDeformation(d[0], d[1], d[2])), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("part");
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

    /** The build whose shape a player shows, or -1 for none (players drawn with their own Minecraft skin). */
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
        if (bulk >= 1) build = BULKY;                                                 // heavy forms wear the biggest build
        boolean fat = state != null && state.has(PublicStatePacket.FUSED_FAT), thin = state != null && state.has(PublicStatePacket.FUSED_THIN);
        if (fat) build = BULKY;
        else if (thin) build = LEAN;
        apply(model, build, bulk);
        if (fat) shape(model, 1.45f, 1.75f, 1.3f, 1.32f);                            // a botched fusion (12c): round as a barrel...
        else if (thin) shape(model, 0.8f, 0.78f, 0.7f, 0.74f);                        // ...or a bag of bones
        Map<String, ModelPart> children = childrenOf(model.body);
        ModelPart belly = children == null ? null : children.get(BELLY);
        if (belly != null) belly.visible = fat;
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event) {
        shape(event.getRenderer().getModel(), 1f, 1f, 1f, 1f);                       // the model is shared: back to vanilla
    }

    /**
     * Shapes a model for a build ({@link #LEAN}, {@link #ATHLETIC}, {@link #BULKY}; -1: vanilla's own shape) and a
     * form's bulk (0..1+: wider and deeper body, thicker arms, sturdier legs). Players and NPCs.
     */
    public static void apply(PlayerModel<?> m, int build, float bulk) {
        Map<String, ModelPart> children = childrenOf(m.body);
        if (children != null) {
            for (int i = 0; i < NAMES.length; i++) {
                ModelPart chest = children.get(NAMES[i]), waist = children.get(WAISTS[i]);
                if (chest != null) chest.visible = i == build;
                if (waist != null) waist.visible = i == build;
            }
        }
        if (build < 0) {
            shape(m, 1f, 1f, 1f, 1f);
            return;
        }
        float[] l = LIMBS[build];
        shape(m, l[0] + 0.1f * bulk, l[1] + 0.14f * bulk, l[2] + 0.2f * bulk, l[3] + 0.05f * bulk, l[4] + 0.09f * bulk);
    }

    /** Swells (or pares down) the torso and limbs for a form's bulk, on the athletic build (first-person arms). */
    static void shape(PlayerModel<?> m, float bulk) {
        float[] l = LIMBS[ATHLETIC];
        shape(m, l[0] + 0.1f * bulk, l[1] + 0.14f * bulk, l[2] + 0.2f * bulk, l[3] + 0.05f * bulk, l[4] + 0.09f * bulk);
    }

    static void shape(PlayerModel<?> m, float body, float depth, float arm, float leg) {
        shape(m, body, depth, arm, leg, leg);
    }

    static void shape(PlayerModel<?> m, float body, float depth, float arm, float legX, float legZ) {
        scale(m.body, body, depth);
        scale(m.jacket, body, depth);
        for (ModelPart p : new ModelPart[]{m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve}) scale(p, arm, arm);
        for (ModelPart p : new ModelPart[]{m.rightLeg, m.leftLeg, m.rightPants, m.leftPants}) scale(p, legX, legZ);
    }

    private static void scale(ModelPart p, float xz, float z) {
        p.xScale = xz;
        p.zScale = z;
    }
}
