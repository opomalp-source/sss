package com.dbzenith.client.render;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.HairCode.Strand;
import com.dbzenith.transform.Form;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Faceted volume renderer shared by stock hair and editable hair. */
public final class HairMesh {
    public static final ResourceLocation MATERIAL=new ResourceLocation("dbzenith","textures/entity/aura_white.png");
    /** Triangles duplicate their last vertex to use Minecraft's quad vertex format. */
    record Segment(float[][] corners,Root strand,float[] distances,float[] progress,boolean scalp,float[][] uv) {}
    record Root(Vec3 root,Vec3 dir,Vec3 normal,float flex,float hang) {}
    private static final Vec3 DOWN=new Vec3(0,1,0);
    private static final Map<String,List<Segment>> CACHE=new LinkedHashMap<>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<Segment>> e) { return size()>128; }
    };
    private HairMesh() {}
    public static List<Segment> cropped() {
        return CACHE.computeIfAbsent("#cropped", k -> { HairGeometry.Builder b=new HairGeometry.Builder(Form.HairStyle.NONE);b.cap(HairGeometry.Cap.BUZZ);return b.build(); });
    }
    public static List<Segment> of(String code) { return CACHE.computeIfAbsent(code,k->buildCode(k,true)); }
    static List<Segment> transition(String from,String to,float progress) {
        if(progress<=0) return of(from);
        if(progress>=1) return of(to);
        List<Segment> authored=SculptedHair.transition(from,to,progress*progress*(3-2*progress));
        // Custom codes retain their editor-defined topology; only named presets have corresponding paths.
        return authored==null?of(from):authored;
    }
    static List<Segment> buildCode(String code,boolean chunky) {
        List<Segment> preset=SculptedHair.create(code); if(preset!=null) return preset;
        List<Strand> strands=HairCode.decode(code); if(strands==null||strands.isEmpty()) return List.of();
        List<Segment> result=new ArrayList<>();
        if(HairCode.hasVolume(code)) { HairGeometry.Builder scalp=new HairGeometry.Builder(Form.HairStyle.NONE); scalp.cap(HairGeometry.Cap.FULL); result.addAll(scalp.build()); }
        for(Strand s:strands) build(s,result,chunky);
        return List.copyOf(result);
    }
    static Vec3[] frame(Strand s) {
        float a=s.u()+.5f,b=s.v()+.5f;
        return switch(s.face()) {
            case TOP -> new Vec3[]{new Vec3(-4+a,-8,-4+b),new Vec3(0,-1,0),new Vec3(1,0,0),new Vec3(0,0,1)};
            case FRONT -> new Vec3[]{new Vec3(-4+a,-8+b,-4),new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(0,1,0)};
            case BACK -> new Vec3[]{new Vec3(4-a,-8+b,4),new Vec3(0,0,1),new Vec3(-1,0,0),new Vec3(0,1,0)};
            case LEFT -> new Vec3[]{new Vec3(4,-8+b,-4+a),new Vec3(1,0,0),new Vec3(0,0,1),new Vec3(0,1,0)};
            case RIGHT -> new Vec3[]{new Vec3(-4,-8+b,4-a),new Vec3(-1,0,0),new Vec3(0,0,-1),new Vec3(0,1,0)};
        };
    }
    static void build(Strand s,List<Segment> out,boolean chunky) {
        Vec3[] f=frame(s); double yaw=Math.toRadians(s.yaw()*15),pitch=Math.toRadians(s.pitch()*15);
        Vec3 dir=f[1].scale(Math.cos(pitch)*Math.cos(yaw)).add(f[2].scale(Math.sin(yaw)*Math.cos(pitch))).add(f[3].scale(-Math.sin(pitch))).normalize();
        Vec3 root=f[0].subtract(f[1].scale(.15)); int count=s.length()<4?3:5;
        double[] knots=new double[count*5]; Vec3 p=root;
        double bend=s.bend()==HairCode.Bend.HANG?.35:s.bend()==HairCode.Bend.DROOP?.15:s.bend()==HairCode.Bend.LIFT?-.18:0;
        for(int i=0;i<count;i++) {
            double t=i/(double)(count-1),w=s.width()*(i==count-1?0:1-.65*t*t); int k=i*5;
            knots[k]=p.x;knots[k+1]=p.y;knots[k+2]=p.z;knots[k+3]=w;knots[k+4]=w*.82;
            dir=dir.add(0,bend,0).normalize();p=p.add(dir.scale(s.length()/(double)(count-1)));
        }
        HairGeometry.Builder builder=new HairGeometry.Builder(Form.HairStyle.NONE);
        builder.tuft(s.bend()==HairCode.Bend.HANG?.65:.25,knots);out.addAll(builder.build());
    }
    public static void render(PoseStack pose,VertexConsumer vc,List<Segment> mesh,int light,int overlay,float r,float g,float b) {
        render(pose,vc,mesh,HairWind.State.STILL,light,overlay,r,g,b,-1);
    }
    public static void render(PoseStack pose,VertexConsumer vc,List<Segment> mesh,HairWind.State wind,int light,int overlay,float r,float g,float b) {
        render(pose,vc,mesh,wind,light,overlay,r,g,b,-1);
    }
    public static void render(PoseStack pose,VertexConsumer vc,List<Segment> mesh,HairWind.State wind,int light,int overlay,float r,float g,float b,int tips) {
        pose.pushPose();pose.scale(1/16f,1/16f,1/16f);var matrix=pose.last().pose();var normals=pose.last().normal();
        Vec3 sag=wind.gravity().subtract(DOWN);
        float tr=tips<0?r:((tips>>16)&255)/255f,tg=tips<0?g:((tips>>8)&255)/255f,tb=tips<0?b:(tips&255)/255f;
        Root previous=null;Vec3 push=Vec3.ZERO;double force=0;float[][] moved=new float[4][3];
        for(Segment face:mesh) {
            Root root=face.strand;
            if(root!=previous) {
                previous=root; Vec3 windForce=wind.wind().scale(root.flex*wind.stiffness()).add(sag.scale(root.hang*.45));
                push=windForce.subtract(root.dir.scale(windForce.dot(root.dir)));force=push.length();if(force>1e-6) push=push.scale(1/force);
            }
            float[][] points=face.corners;
            if(!face.scalp&&force>1e-4) {
                for(int i=0;i<4;i++) {
                    float distance=face.distances[i];double delta=Math.min(Math.min(2.2,.12*distance)*wind.stiffness(),force*14.0*Math.pow(distance/16.0,1.25));
                    double back=distance-Math.sqrt(Math.max(0,distance*distance-delta*delta));float[] p=points[i];
                    moved[i][0]=(float)(p[0]+push.x*delta-root.dir.x*back);moved[i][1]=(float)(p[1]+push.y*delta-root.dir.y*back);moved[i][2]=(float)(p[2]+push.z*delta-root.dir.z*back);
                }
                points=moved;
            }
            float[] a=points[0],c=points[1],d=points[2];
            float ax=c[0]-a[0],ay=c[1]-a[1],az=c[2]-a[2],bx=d[0]-a[0],by=d[1]-a[1],bz=d[2]-a[2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<1e-8) continue; nx/=length;ny/=length;nz/=length;
            for(int i=0;i<4;i++) {
                float t=face.progress[i],blend=Math.max(0,Math.min(1,(t-.45f)/.55f));blend=blend*blend*(3-2*blend);
                float cr=r+(tr-r)*blend,cg=g+(tg-g)*blend,cb=b+(tb-b)*blend,lift=.095f*(1-Math.max(r,Math.max(g,b)));
                cr+=(1-cr)*lift;cg+=(1-cg)*lift;cb+=(1-cb)*lift;float[] p=points[i];
                vc.vertex(matrix,p[0],p[1],p[2]).color(cr,cg,cb,1).uv(face.uv[i][0],face.uv[i][1]).overlayCoords(overlay).uv2(light).normal(normals,nx,ny,nz).endVertex();
            }
        }
        pose.popPose();
    }
}
