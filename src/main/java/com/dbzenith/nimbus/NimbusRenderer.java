package com.dbzenith.nimbus;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** One sculpted, closed cloud surface. All billows, creases and curls belong to that surface. */
public class NimbusRenderer extends EntityRenderer<NimbusEntity> {
    private static final ResourceLocation WHITE=new ResourceLocation("dbzenith","textures/entity/aura_white.png");
    private static final int SLICES=192,RINGS=80;
    private record Curl(double angle,double latitude,double radius,double turn) {}
    private static final Curl[] CURLS={new Curl(.15,.15,.44,1),new Curl(1.28,.22,.46,-1),
        new Curl(2.45,.12,.41,1),new Curl(3.58,.22,.47,-1),new Curl(4.85,.12,.4,1),
        new Curl(5.85,.65,.32,-1),new Curl(2.15,.92,.3,1)};
    private record Sample(Vec3 p,Vec3 normal,float groove,float highlight) {}
    private static final Sample[][] MESH=mesh();
    public NimbusRenderer(EntityRendererProvider.Context c) { super(c);shadowRadius=.85f; }
    @Override public ResourceLocation getTextureLocation(NimbusEntity e) { return WHITE; }
    @Override public boolean shouldRender(NimbusEntity e,Frustum f,double x,double y,double z) { return f.isVisible(e.getBoundingBox().inflate(35)); }
    @Override public void render(NimbusEntity n,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();pose.translate(0,Math.sin((n.tickCount+partial)*.09)*.025,0);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        drawCloud(pose,buffers,light,n.tickCount+partial);pose.popPose();
        Vec3 origin=new Vec3(net.minecraft.util.Mth.lerp(partial,n.xo,n.getX()),net.minecraft.util.Mth.lerp(partial,n.yo,n.getY()),net.minecraft.util.Mth.lerp(partial,n.zo,n.getZ()));
        var points=new ArrayList<Vec3>();var lives=new ArrayList<Float>();
        for(var p:n.wake) {
            float life=(float)Math.max(0,1-(n.tickCount+partial-p.born())/36.0);
            if(life>0) { points.add(p.position().subtract(origin).add(0,(1-life)*.12,0));lives.add(life); }
        }
        if(points.size()>1) drawWake(pose,buffers.getBuffer(RenderType.entityTranslucent(WHITE)),points,lives,light);
        super.render(n,yaw,partial,pose,buffers,light);
    }
    public static void drawCloud(PoseStack pose,MultiBufferSource buffers,int light,float time) {
        VertexConsumer vc=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        for(int j=0;j<RINGS;j++) for(int i=0;i<SLICES;i++) {
            emit(pose,vc,MESH[j][i],light);emit(pose,vc,MESH[j+1][i],light);
            emit(pose,vc,MESH[j+1][i+1],light);emit(pose,vc,MESH[j][i+1],light);
        }
    }
    private static Sample[][] mesh() {
        Sample[][] mesh=new Sample[RINGS+1][SLICES+1];
        for(int j=0;j<=RINGS;j++) for(int i=0;i<=SLICES;i++) {
            double a=i*Math.PI*2/SLICES,b=-Math.PI/2+j*Math.PI/RINGS;
            Vec3 p=surface(a,b);
            Vec3 da=surface(a+.002,b).subtract(surface(a-.002,b));
            Vec3 db=surface(a,Math.min(Math.PI/2-.0001,b+.002)).subtract(surface(a,Math.max(-Math.PI/2+.0001,b-.002)));
            Vec3 normal=db.normalize().cross(da.normalize()).normalize();
            if(j==0||j==RINGS) normal=new Vec3(0,j==0?-1:1,0);
            double d=curlDistance(a,b);
            mesh[j][i]=new Sample(p,normal,(float)Math.exp(-d*d/.00065),(float)Math.exp(-Math.pow(d-.039,2)/.0005));
        }
        return mesh;
    }
    private static double wrap(double a) { return Math.atan2(Math.sin(a),Math.cos(a)); }
    private static double curlDistance(double a,double b) {
        double best=10;
        for(Curl c:CURLS) {
            double x=wrap(a-c.angle)*Math.cos(c.latitude),y=b-c.latitude;
            if(Math.abs(x)>c.radius+.08||Math.abs(y)>c.radius+.08) continue;
            double px=0,py=0;
            for(int k=0;k<=64;k++) {
                double t=k/64.0,angle=t*Math.PI*2.65*c.turn+.6,r=c.radius*(1-t*.92);
                double qx=Math.cos(angle)*r,qy=Math.sin(angle)*r;
                if(k>0) {
                    double vx=qx-px,vy=qy-py,u=Math.max(0,Math.min(1,((x-px)*vx+(y-py)*vy)/(vx*vx+vy*vy)));
                    best=Math.min(best,Math.hypot(x-px-u*vx,y-py-u*vy));
                }
                px=qx;py=qy;
            }
        }
        return best;
    }
    private static Vec3 surface(double a,double b) {
        double cb=Math.cos(b),sb=Math.sin(b);
        // Broad pillow top and scalloped rim: no intersecting balls or separate discs.
        double rim=Math.exp(-Math.pow((b-.05)/.65,2));
        double billow=.075*Math.cos(7*a+.5)+.04*Math.cos(11*a-1.1)+.025*Math.sin(4*a);
        double r=1+rim*billow+.025*Math.sin(3*a+b*4)*cb;
        double d=curlDistance(a,b);
        r-=.012*Math.exp(-d*d/.0025);
        double x=cb*Math.cos(a)*r;
        double z=cb*Math.sin(a)*r*1.17;
        double y=.37+.44*sb;
        y+=.065*rim*Math.cos(7*a+.5)+.025*cb*Math.sin(3*a);
        y+=Math.max(0,sb)*cb*cb*(.24*Math.cos(3*a+b*2)+.10*Math.sin(5*a-b*3));
        // Sweep the rear into a pointed wisp which feeds the flight wake.
        double rear=Math.pow(Math.max(0,-Math.sin(a)),22)*Math.pow(cb,7);
        z-=.75*rear;y+=.10*rear;
        return new Vec3(x,y,z);
    }
    private static void emit(PoseStack pose,VertexConsumer vc,Sample s,int light) {
        float upper=(float)Math.max(0,s.normal.y);
        float fold=s.groove,shine=s.highlight;
        float r=1-.18f*fold,g=.83f+.09f*upper-.16f*fold+.035f*shine,b=.18f+.20f*upper-.08f*fold+.06f*shine;
        vertex(pose,vc,s.p,s.normal,r,g,b,1,light);
    }
    private static void drawWake(PoseStack pose,VertexConsumer vc,List<Vec3> points,List<Float> lives,int light) {
        Vec3[][] ring=new Vec3[points.size()][16];Vec3[][] normals=new Vec3[points.size()][16];
        for(int i=0;i<points.size();i++) {
            Vec3 tangent=points.get(Math.min(i+1,points.size()-1)).subtract(points.get(Math.max(0,i-1))).normalize();
            Vec3 u=tangent.cross(Math.abs(tangent.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=tangent.cross(u).normalize();
            double radius=.015+.30*Math.pow(lives.get(i),1.1);
            for(int j=0;j<16;j++) {
                double a=j*Math.PI/8;
                Vec3 normal=u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                normals[i][j]=normal;ring[i][j]=points.get(i).add(normal.scale(radius*(1+.07*Math.sin(a*3+i*.22))));
            }
        }
        for(int i=0;i<points.size()-1;i++) for(int j=0;j<16;j++) {
            int k=(j+1)%16;
            wakeVertex(pose,vc,ring[i][j],normals[i][j],lives.get(i),light);
            wakeVertex(pose,vc,ring[i][k],normals[i][k],lives.get(i),light);
            wakeVertex(pose,vc,ring[i+1][k],normals[i+1][k],lives.get(i+1),light);
            wakeVertex(pose,vc,ring[i+1][j],normals[i+1][j],lives.get(i+1),light);
        }
    }
    private static void wakeVertex(PoseStack pose,VertexConsumer vc,Vec3 p,Vec3 n,float life,int light) { vertex(pose,vc,p,n,1,.86f,.27f,life*.72f,light); }
    private static void vertex(PoseStack pose,VertexConsumer vc,Vec3 p,Vec3 n,float r,float g,float b,float alpha,int light) {
        vc.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(r,g,b,alpha).uv(.5f,.5f)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
