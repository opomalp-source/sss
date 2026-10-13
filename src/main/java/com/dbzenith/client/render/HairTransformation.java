package com.dbzenith.client.render;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Forms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import java.util.Map;
import java.util.WeakHashMap;

/** Continuous shape progress, independent of the flashing transformation colour. */
public final class HairTransformation {
    public record Frame(String target,float progress) {}
    private static final class Track { String target="";float time,progress,elapsed; }
    private static final Map<AbstractClientPlayer,Track> TRACKS=new WeakHashMap<>();
    private HairTransformation() {}
    public static boolean goldTarget(String id) {
        return switch(id) {
            case "super_saiyan","super_saiyan_g2","super_saiyan_g3","super_saiyan_2","super_saiyan_3" -> true;
            default -> false;
        };
    }
    public static int chargeColor(int normal,int gold,float progress,float age) {
        // A readable 0.8-second cycle. Gold holds longer as the transformation stabilizes.
        float duty=3+8*Mth.clamp(progress,0,1);
        return age%16<duty?gold:normal;
    }
    public static Frame sample(AbstractClientPlayer player,PublicStatePacket state,float partial) {
        String target=state.transformTarget();
        if(target.isEmpty()||!Forms.exists(target)||target.equals(state.form())) {
            TRACKS.remove(player);return new Frame("",0);
        }
        float now=player.tickCount+partial;
        Track t=TRACKS.computeIfAbsent(player,p->new Track());
        boolean fresh=!target.equals(t.target)||now<t.time||now-t.time>20;
        if(fresh) {
            t.target=target;t.progress=0;t.elapsed=0;t.time=now;
        }
        float dt=Mth.clamp(now-t.time,0,2);t.time=now;
        boolean held=state.has(PublicStatePacket.TRANSFORMING);
        float desired;
        if(player==Minecraft.getInstance().player&&ClientPlayerData.hasData()) {
            var data=ClientPlayerData.get();
            desired=target.equals(data.getTransformTarget())?
                Mth.clamp((data.getTransformTicks()+(held?partial:0))/Math.max(1f,data.getTransformTotal()),0,1):0;
        } else {
            // Public appearance packets contain the target but not private mastery/progress.
            t.elapsed=Math.max(0,t.elapsed+dt*(held?1:-.5f));
            desired=Math.min(.97f,t.elapsed/Math.max(20,2*(24+14*Forms.byId(target).tier())));
        }
        if(fresh) t.progress=desired;
        else t.progress+=(desired-t.progress)*(1-(float)Math.exp(-dt*.65));
        return new Frame(target,Mth.clamp(t.progress,0,1));
    }
}
