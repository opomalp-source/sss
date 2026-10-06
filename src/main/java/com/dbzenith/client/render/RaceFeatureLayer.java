package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.race.RaceTraits;
import com.dbzenith.race.Races;
import com.dbzenith.race.Variant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws racial body parts and the Saiyan tail on any player, from their public state, coloured to match the skin
 * they wear now (an Orange Namekian's antennae turn orange, an evil Majin's tentacle grey, a golden Frost Demon's
 * horns gold). The tail lifts behind a runner, streams behind a flier, swings and settles, and wraps round the waist
 * while its owner crouches.
 */
public class RaceFeatureLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/race_parts.png");
    private static final int TAIL = 0x6B3E1E;

    /** Each player's eased tail lift (0 hanging .. 1 straight out). */
    private static final Map<Integer, float[]> LIFT = new HashMap<>();

    private final RaceFeatureModel model;

    public RaceFeatureLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new RaceFeatureModel(models.bakeLayer(RaceFeatureModel.LAYER));
    }

    /** The colour of a feature, from the skin the player wears now. */
    static int tint(PublicStatePacket state, RaceTraits.Feature feature, Variant variant) {
        String skin = state.raceLook() ? com.dbzenith.transform.FormLooks.skin(state.form(), RaceSkinLayer.skinName(state.raceEnum(), variant)) : null;
        String s = skin == null ? "" : skin;
        return switch (feature) {
            case ANTENNAE -> s.equals("namekian_orange") ? 0xE8902A : s.equals("demon_namekian_king") ? 0x8A2A36
                    : s.startsWith("demon_namekian") ? 0x3A8A6A : 0x62B444;
            case HORNS -> s.equals("frost_demon_golden") ? 0xE8C050 : s.startsWith("metal_frost_demon") ? (s.endsWith("core") ? 0xE0B050 : 0xB8C4D0)
                    : s.startsWith("mutant_frost_demon") ? 0x3A3040 : 0xEDE6D6;
            case TENTACLE -> switch (s) {
                case "majin_evil" -> 0x8C8C94;
                case "majin_pure" -> 0xF6A8C8;
                case "corrupted_majin" -> 0x9A90A8;
                case "corrupted_majin_pure" -> 0x6A3A8A;
                default -> 0xF59AC0;
            };
            case EARS -> state.skinTone() >= 0 ? state.skinTone() : variant == Variant.KAI ? 0xD8B8EC : 0xE6E0EA;
            case WINGS -> s.equals("bio_android_zenith") ? 0xD0A040 : s.equals("bio_android_perfect") ? 0x3AA07A : 0x5AB04A;
            case DEMON_HORNS -> 0x2A1418;
            default -> 0xFFFFFF;
        };
    }

    /** A part in the colours the player chose (CX-16b): skin-coloured parts take the skin colour, horns the part colour. */
    static int chosen(PublicStatePacket state, RaceTraits.Feature feature, com.dbzenith.appearance.RaceCustom.Part part, int fallback) {
        int c = switch (feature) {
            case HORNS, DEMON_HORNS -> state.racePart();
            default -> state.raceSkin();
        };
        return c >= 0 ? c : fallback;
    }

    /** The race skin a player wears now, or "". */
    static String s(PublicStatePacket state, Variant variant) {
        return String.valueOf(com.dbzenith.transform.FormLooks.skin(state.form(), RaceSkinLayer.skinName(state.raceEnum(), variant)));
    }

    /** The colour of a Frost Demon's shell (dome, plates, the third form's crest). */
    static int shellColor(PublicStatePacket state, Variant variant) {
        String s = String.valueOf(com.dbzenith.transform.FormLooks.skin(state.form(), RaceSkinLayer.skinName(state.raceEnum(), variant)));
        if (s.contains("golden") || s.endsWith("core")) return 0xE8C050;
        if (s.startsWith("metal")) return 0x6A7A90;
        if (s.startsWith("mutant")) return 0xE84AB0;
        return 0x8A4AC8;
    }

    static int tailColor(PublicStatePacket state) {
        String form = state.form();
        if (form.contains("limit_breaker")) return 0xD8DCE6;
        if (form.contains("super_saiyan_4") || form.contains("ssj4") || form.contains("lssj4")) return 0xC0283A;
        if (form.contains("golden")) return 0xE8B840;
        return TAIL;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        Variant variant = state.variantEnum();
        RaceTraits.Feature feature = variant.feature() != null ? variant.feature() : Races.of(state.raceEnum()).feature();
        com.dbzenith.appearance.RaceCustom.Part part = com.dbzenith.appearance.RaceCustom.of(state.raceEnum(), variant).part();
        int style = Math.max(0, Math.min(3, state.raceStyle()));
        if (part == com.dbzenith.appearance.RaceCustom.Part.ALIEN) {             // a Gen Alien's pick (CX-16b)
            feature = switch (style) {
                case 1 -> RaceTraits.Feature.DEMON_HORNS;
                case 2 -> RaceTraits.Feature.ANTENNAE;
                case 3 -> RaceTraits.Feature.HORNS;
                default -> RaceTraits.Feature.NONE;
            };
        }
        float length = part == com.dbzenith.appearance.RaceCustom.Part.ALIEN || part == com.dbzenith.appearance.RaceCustom.Part.NONE
                ? 1f : com.dbzenith.appearance.RaceCustom.LENGTH[style];
        boolean own = RaceSkinLayer.custom(state) != null || RaceSkinLayer.skinName(state.raceEnum(), variant) == null;   // chosen colours show
        if (feature == RaceTraits.Feature.NONE && state.skinTone() >= 0
                && com.dbzenith.appearance.FaceParts.get(state.face(), com.dbzenith.appearance.FaceParts.Part.EARS) == com.dbzenith.appearance.FaceParts.EARS_POINTED) {
            feature = RaceTraits.Feature.EARS;                                     // chosen in the Face screen
        }
        boolean tail = state.has(PublicStatePacket.TAIL);
        boolean ridge = FormShape.browRidge(state.form()), crest = feature == RaceTraits.Feature.HORNS && FormShape.crest(state.form());
        if (feature == RaceTraits.Feature.NONE && !tail && !ridge) return;

        model.follow(getParentModel().head, getParentModel().body);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = OverlayTexture.NO_OVERLAY;
        if (feature != RaceTraits.Feature.NONE) {
            int c = tint(state, feature, variant);
            if (own) c = chosen(state, feature, part, c);
            boolean namekEars = feature == RaceTraits.Feature.ANTENNAE;           // Namekians have the long ears too
            RaceTraits.Feature shown = namekEars && GearLayer.turban(player) ? RaceTraits.Feature.NONE : feature;   // tucked under a turban
            float horn = FormShape.hornScale(state.form()) * (part == com.dbzenith.appearance.RaceCustom.Part.HORNS ? length : 1f);
            model.renderFeature(pose, vc, light, overlay, shown, namekEars, horn, length, r(c), g(c), b(c));
            if (shown == RaceTraits.Feature.HORNS && part == com.dbzenith.appearance.RaceCustom.Part.HORNS) {   // Frost Demon ear plates: shell
                int sc = own && state.raceMark() >= 0 ? state.raceMark() : shellColor(state, variant);
                model.renderHornPlates(pose, vc, light, overlay, r(sc), g(sc), b(sc));
            }
            if (crest) {
                int sk = s(state, variant).startsWith("mutant") ? 0x2A2230 : s(state, variant).startsWith("metal") ? 0xD8E2EC : 0xF0EEF4;
                if (own && state.raceSkin() >= 0) sk = state.raceSkin();
                model.renderHeadShape(pose, vc, light, overlay, "crest", r(sk), g(sk), b(sk));      // the long pale skull
                int sc = own && state.raceMark() >= 0 ? state.raceMark() : shellColor(state, variant);
                model.renderHeadShape(pose, vc, light, overlay, "crest_ridge", r(sc), g(sc), b(sc)); // its shell ridge
            }
        }
        if (ridge) {                                                                // Super Saiyan 3's brow
            int sc = state.skinTone() >= 0 ? state.skinTone() : 0xE8B890;
            model.renderHeadShape(pose, vc, light, overlay, "brow_ridge", r(sc) * 0.92f, g(sc) * 0.92f, b(sc) * 0.92f);
        }
        if (tail) {
            int c = tailColor(state);
            if (c == TAIL && state.racePart() >= 0) c = state.racePart();   // the chosen fur, unless a form colours it
            float size = variant == Variant.LEGENDARY_PRIMAL ? 1.45f : variant == Variant.PRIMAL ? 1.3f : 1f;   // the Primal clans' great tails
            model.renderTail(pose, vc, light, overlay, ageInTicks, lift(player, state), player.isCrouching(), size, r(c), g(c), b(c));
        }
    }

    /** How far the tail streams out: with speed along the body, with falling, and fully in flight; eased. */
    static float lift(AbstractClientPlayer player, PublicStatePacket state) {
        Vec3 v = player.getDeltaMovement();
        double yaw = Math.toRadians(player.yBodyRot);
        double forward = -v.x * Math.sin(yaw) + v.z * Math.cos(yaw);
        float want = (float) Mth.clamp(forward * 4.5, -0.2, 0.9);
        if (!player.onGround()) want += (float) Mth.clamp(-v.y * 1.5, 0, 0.5);
        if (state.has(PublicStatePacket.FLYING)) want = Math.max(want, 0.55f + (float) Mth.clamp(v.horizontalDistance() * 2, 0, 0.45));
        float target = Mth.clamp(want, 0, 1);
        float[] l = LIFT.computeIfAbsent(player.getId(), k -> new float[]{target});
        l[0] += (target - l[0]) * 0.08f;
        if (LIFT.size() > 256) LIFT.clear();
        return l[0];
    }

    private static float r(int c) { return ((c >> 16) & 0xFF) / 255f; }
    private static float g(int c) { return ((c >> 8) & 0xFF) / 255f; }
    private static float b(int c) { return (c & 0xFF) / 255f; }
}
