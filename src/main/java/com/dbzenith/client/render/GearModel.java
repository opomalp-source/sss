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
        HEADBAND("headband"), HEADBAND_TAILS("headband_tails"),
        CHEST_PLATE("chest_plate"), ABDOMEN("abdomen"), STRAPS("straps"), HIP_GUARDS("hip_guards"),
        GI_LAPELS("gi_lapels"), SASH_KNOT("sash_knot"), FUSION_COLLAR("fusion_collar"),
        HOOD_TRIM("hood_trim"), MAJIN_BUCKLE("majin_buckle"), MAJIN_MARK("majin_mark");

        final String part;

        Piece(String part) {
            this.part = part;
        }
    }

    private final ModelPart head, body, rightArm, leftArm, rightLeg, leftLeg;
    private final ModelPart capeRoot = capeChain(0);
    private float capeForwardTilt;

    public void capeForwardTilt(float radians) { capeForwardTilt = Math.max(0, radians); }

    private static ModelPart capeChain(int index) {
        var children = index + 1 < CAPE_PANELS ? java.util.Map.of("cape_" + (index + 1), capeChain(index + 1))
                : java.util.Map.<String, ModelPart>of();
        var part = new ModelPart(java.util.List.of(new CapePanel(index)), children);
        part.setPos(0, index == 0 ? -.4f : 5.6f, index == 0 ? 3.25f : 0);
        return part;
    }

    /** A continuous pleated trapezoid: no stair-step outline or open spaces between separate fold boxes. */
    private static final class CapePanel extends ModelPart.Cube {
        private final java.util.List<float[]> faces = new java.util.ArrayList<>();
        CapePanel(int index) {
            super(0,0,0,0,0,0,0,0,0,0,0,false,64,64,java.util.EnumSet.noneOf(net.minecraft.core.Direction.class));
            float top = 11.6f + index * 1.1f, bottom = top + 1.1f, h = 5.78f;
            for (int j = 0; j < 8; j++) {
                float a = j / 8f - .5f, b = (j + 1) / 8f - .5f;
                float za = .15f + (j % 2) * .42f, zb = .15f + ((j + 1) % 2) * .42f;
                float[] tl={a*top,0,za}, tr={b*top,0,zb}, bl={a*bottom,h,za}, br={b*bottom,h,zb};
                float[] il={a*top,0,.03f}, ir={b*top,0,.03f}, jl={a*bottom,h,.03f}, jr={b*bottom,h,.03f};
                float u=1+j*3, v=32+index*6;
                quad(tl,bl,br,tr,u,v,u+3,v+5.78f); // outer folded fabric
                quad(ir,jr,jl,il,u,v,u+3,v+5.78f); // continuous inside surface
                quad(il,tl,tr,ir,u,v,u+3,v+.5f);
                quad(bl,jl,jr,br,u,v+5.25f,u+3,v+5.78f);
                if(j==0)quad(il,jl,bl,tl,u,v,u+.5f,v+5.78f);
                if(j==7)quad(tr,br,jr,ir,u,v,u+.5f,v+5.78f);
            }
        }
        private void quad(float[] a,float[] b,float[] c,float[] d,float u,float v,float u1,float v1) {
            float[] f=new float[23]; float[][] p={a,b,c,d}; float[][] uv={{u,v},{u,v1},{u1,v1},{u1,v}};
            for(int i=0;i<4;i++){System.arraycopy(p[i],0,f,i*5,3);f[i*5+3]=uv[i][0]/64;f[i*5+4]=uv[i][1]/64;}
            float x=b[0]-a[0],y=b[1]-a[1],z=b[2]-a[2],xx=c[0]-a[0],yy=c[1]-a[1],zz=c[2]-a[2];
            float nx=y*zz-z*yy,ny=z*xx-x*zz,nz=x*yy-y*xx,len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            f[20]=-nx/len;f[21]=-ny/len;f[22]=-nz/len;faces.add(f);
        }
        @Override public void compile(PoseStack.Pose pose,VertexConsumer vc,int light,int overlay,float r,float g,float b,float alpha) {
            for(float[] f:faces)for(int i=0;i<4;i++){
                int k=i*5;
                vc.vertex(pose.pose(),f[k]/16,f[k+1]/16,f[k+2]/16).color(r,g,b,alpha).uv(f[k+3],f[k+4])
                        .overlayCoords(overlay).uv2(light).normal(pose.normal(),f[20],f[21],f[22]).endVertex();
            }
        }
    }

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

        // Piccolo's weighted mantle stays on the torso rather than flapping with every arm swing.
        PartDefinition mantle = body.addOrReplaceChild("namek_pads", CubeListBuilder.create()
                .texOffs(CLOTH, 0).addBox(-8.6f, -1.9f, -3.0f, 5.4f, 1.15f, 6.4f)
                .texOffs(CLOTH, 0).addBox(3.2f, -1.9f, -3.0f, 5.4f, 1.15f, 6.4f)
                .texOffs(CLOTH, 0).addBox(-8.0f, -2.65f, -2.5f, 4.8f, .8f, 5.5f)
                .texOffs(CLOTH, 0).addBox(3.2f, -2.65f, -2.5f, 4.8f, .8f, 5.5f)
                .texOffs(CLOTH, 0).addBox(-3.4f, -2.7f, -2.8f, 1.1f, 3.7f, 5.9f)
                .texOffs(CLOTH, 0).addBox(2.3f, -2.7f, -2.8f, 1.1f, 3.7f, 5.9f)
                .texOffs(CLOTH, 0).addBox(-2.3f, -2.7f, 2.1f, 4.6f, 3.7f, 1.0f), PartPose.ZERO);
        // Registration anchor; the continuous tapered cloth mesh is baked once into capeRoot.
        body.addOrReplaceChild("cape", CubeListBuilder.create(), PartPose.ZERO);

        // Sculpted torso shell with a separate flexible ribbed abdomen and raised shoulder straps.
        body.addOrReplaceChild("chest_plate", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.55f, .3f, -3.55f, 9.1f, 4.4f, .65f)
                .texOffs(32, 0).addBox(-4.15f, .75f, -3.8f, 8.3f, 3.15f, .3f)
                .texOffs(32, 0).addBox(-4.3f, .5f, 2.95f, 8.6f, 7.7f, .45f), PartPose.ZERO);
        CubeListBuilder abdomen = CubeListBuilder.create();
        for (int i = 0; i < 4; i++) abdomen.texOffs(32, 16)
                .addBox(-3.65f + i * .14f, 4.85f + i * .9f, -3.35f, 7.3f - i * .28f, .72f, .52f);
        body.addOrReplaceChild("abdomen", abdomen, PartPose.ZERO);
        body.addOrReplaceChild("straps", CubeListBuilder.create().texOffs(32, 16)
                .addBox(-4.2f, -.3f, -3.5f, 1.5f, 1.4f, 6.7f)
                .texOffs(32, 16).addBox(2.7f, -.3f, -3.5f, 1.5f, 1.4f, 6.7f), PartPose.ZERO);
        PartDefinition hips = body.addOrReplaceChild("hip_guards", CubeListBuilder.create(), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            CubeListBuilder ribs = CubeListBuilder.create().texOffs(32, 0).addBox(-1.55f, 0, -2.8f, 3.1f, 4.2f, 5.6f);
            for (int i = 0; i < 4; i++) ribs.texOffs(32, 16).addBox(-1.38f, .15f + i * .95f, -3.02f, 2.76f, .7f, .35f);
            hips.addOrReplaceChild("hip_" + side, ribs, PartPose.offsetAndRotation(side * 4.8f, 9.4f, 0, 0, 0, -side * .23f));
        }
        hips.addOrReplaceChild("front_guard", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-3.5f, 0, 0, 7, 3.8f, .5f), PartPose.offsetAndRotation(0, 9.5f, -2.9f, -.22f, 0, 0));
        // Garment construction: folded lapels, wrapped obi and a knot, not just ink on the skin.
        PartDefinition lapels = body.addOrReplaceChild("gi_lapels", CubeListBuilder.create(), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) lapels.addOrReplaceChild("lapel_" + side,
                CubeListBuilder.create().texOffs(0, 0).addBox(-.36f, 0, -.15f, .72f, 5.7f, .42f),
                PartPose.offsetAndRotation(side * 3.3f, .05f, -3.2f, 0, 0, side * .59f));
        body.addOrReplaceChild("sash_knot", CubeListBuilder.create().texOffs(32, 16)
                .addBox(-4.4f, 9.35f, -2.95f, 8.8f, 1.3f, .45f)
                .texOffs(32, 16).addBox(-1.75f, 9.4f, -3.45f, 1.7f, 1.35f, .8f), PartPose.ZERO);
        PartDefinition collar = body.addOrReplaceChild("fusion_collar", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-3.2f, -1.3f, 2.4f, 6.4f, 1.6f, 1.4f), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            collar.addOrReplaceChild("vest_roll_" + side, CubeListBuilder.create().texOffs(32, 0)
                    .addBox(-.7f, 0, -.7f, 1.4f, 7.7f, 1.4f), PartPose.offsetAndRotation(side * 2.45f, -.4f, -3.0f, 0, 0, -side * .06f));
            collar.addOrReplaceChild("vest_shoulder_" + side, CubeListBuilder.create().texOffs(32, 0)
                    .addBox(-2, -.6f, -2.7f, 4, 1.3f, 5.4f), PartPose.offset(side * 4.2f, -.6f, 0));
        }
        body.addOrReplaceChild("hood_trim", CubeListBuilder.create().texOffs(32, 16)
                .addBox(-.14f, .3f, -3.18f, .28f, 9.7f, .28f)
                .texOffs(0, 0).addBox(-3.1f, 6.6f, -3.05f, 2.8f, 2.5f, .35f)
                .texOffs(0, 0).addBox(.3f, 6.6f, -3.05f, 2.8f, 2.5f, .35f), PartPose.ZERO);
        body.addOrReplaceChild("majin_buckle", CubeListBuilder.create().texOffs(32, 16)
                .addBox(-1.05f, 9.1f, -3.25f, 2.1f, 1.75f, .55f), PartPose.ZERO);

        PartDefinition mark = body.addOrReplaceChild("majin_mark", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-.7f, 9.35f, -3.84f, .18f, 1.1f, .12f)
                .texOffs(32, 0).addBox(.52f, 9.35f, -3.84f, .18f, 1.1f, .12f), PartPose.ZERO);
        for(int side : new int[]{-1,1}) mark.addOrReplaceChild("m_"+side, CubeListBuilder.create().texOffs(32,0)
                .addBox(-.08f,0,0,.16f,.85f,.12f), PartPose.offsetAndRotation(side*.5f,9.35f,-3.85f,0,0,side*.65f));

        // Piccolo's turban: a low purple crown inside layered ivory wraps.
        head.addOrReplaceChild("turban", CubeListBuilder.create().texOffs(CLOTH, 0)
                .addBox(-4.45f, -8.1f, -4.45f, 8.9f, 2.35f, 8.9f)
                .texOffs(CLOTH, 0).addBox(-4.55f, -7.55f, -4.55f, 9.1f, .4f, 9.1f)
                .texOffs(CLOTH, 0).addBox(-4.35f, -8.55f, -4.35f, 8.7f, .65f, 8.7f), PartPose.ZERO);
        head.addOrReplaceChild("turban_band", CubeListBuilder.create().texOffs(CLOTH, 0)
                .addBox(-3.75f, -9.3f, -3.75f, 7.5f, .9f, 7.5f), PartPose.ZERO);

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
        float x0 = side < 0 ? -5.0f : -.7f;
        CubeListBuilder ribs = CubeListBuilder.create();
        for (int i = 0; i < 5; i++) ribs.texOffs(32, 16).addBox(x0 + i * .98f, -2.65f, -3, .85f, .8f, 6);
        arm.addOrReplaceChild("pads", ribs, PartPose.rotation(0, 0, tilt * .4f));
        arm.addOrReplaceChild("pad_lip", CubeListBuilder.create().texOffs(32, 0)
                .addBox(x0 - .15f, -2.15f, -3.15f, 5.2f, .45f, 6.3f), PartPose.rotation(0, 0, tilt * .4f));
        arm.addOrReplaceChild("pad_trim", CubeListBuilder.create().texOffs(32, 0)
                .addBox(side < 0 ? -5.25f : 4.15f, -2.75f, -3.2f, .45f, 1.2f, 6.4f), PartPose.rotation(0, 0, tilt * .4f));
    }

    public void follow(PlayerModel<?> m) {
        capeForwardTilt = 0;
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
            ModelPart p = piece == Piece.CAPE ? capeRoot : parent.getChild(name);
            if (piece == Piece.CAPE) {
                if (cloth == null) p.xRot = 0.06f + 1.1f * capeLift;
                else {                                                         // each panel swings from the one above it
                    ModelPart panel = p;
                    for (int i = 0; i < CAPE_PANELS; i++) {
                        int s = Math.min(i, cloth.segments() - 1);
                        panel.xRot = Math.max(i == 0 ? 0.04f : -0.5f, cloth.relPitch(s) - (i == 0 ? capeForwardTilt : 0));
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
