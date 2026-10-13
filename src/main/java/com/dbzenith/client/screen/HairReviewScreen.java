package com.dbzenith.client.screen;

import com.dbzenith.DBZenith;
import com.dbzenith.appearance.HairCode;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/** Review-only client harness. Enabled solely by a JVM property and excluded from the release patch. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class HairReviewScreen extends Screen {
    private static int joined;
    private int page, frames;
    private boolean capturePending;
    private static final float[] YAW={0,45,90,135,180,225,270,315,0};
    private static final String[] ANGLES={"Front","Front left","Left","Rear left","Rear","Rear right","Right","Front right","Top"};
    public HairReviewScreen() { super(Component.literal("Hair geometry review")); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!Boolean.getBoolean("dbzenith.devHairReview")||e.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null) return;
        if(++joined==60) { mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new HairReviewScreen()); }
    }

    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        var player=minecraft.player;if(player==null) return;
        HairCode.Preset[] presets=HairCode.Preset.values();
        HairCode.Preset preset=page<presets.length?presets[page]:HairCode.Preset.SPIKY;
        String form=page==presets.length?"super_saiyan":page==presets.length+1?"super_saiyan_3":page==presets.length+2?"super_saiyan_blue":"base";
        String suffix=form.equals("base")?"":" — "+form;
        g.fill(0,0,width,height,0xFFB8C0CD);
        g.drawString(font,"Minecraft renderer — "+Component.translatable(preset.translationKey()).getString()+suffix+" — nine angles",18,12,0xFF172334,false);
        PublicStatePacket original=ClientPublicStates.get(player.getId());
        PlayerData data=new PlayerData();data.initDefaultsIfNeeded();data.setHairCode(preset.code());
        data.setFormId(form);
        data.setHairColor(preset==HairCode.Preset.CURTAINS?0x9A88C2:0x111218);
        data.setSkinTone(0xD8A779);data.setEyeColor(0x25202A);
        boolean transforming=page>=presets.length+3;
        if(transforming) {
            data.startTransforming("super_saiyan",200);
            data.setTransformTicks(new int[]{20,80,140,190}[page-presets.length-3]);
            com.dbzenith.client.ClientPlayerData.apply(data.writeSyncTag());
        }
        PublicStatePacket preview=PublicStatePacket.of(player.getId(),data).withFlags(transforming?PublicStatePacket.TRANSFORMING:0,"");
        boolean longHair=form.equals("super_saiyan_3")||preset==HairCode.Preset.MANE||preset==HairCode.Preset.SAGE||preset==HairCode.Preset.LONG||preset==HairCode.Preset.PONYTAIL||preset==HairCode.Preset.TWINTAILS;
        int cw=width/3,ch=(height-36)/3,scale=(int)(Math.min(cw,ch)*(longHair?.34:.50));
        try {
            ClientPublicStates.put(preview);
            for(int i=0;i<9;i++) {
                int x=(i%3)*cw,y=36+(i/3)*ch;
                g.fill(x+3,y+3,x+cw-3,y+ch-3,0xFFC9D0DB);
                g.drawString(font,ANGLES[i],x+12,y+10,0xFF172334,false);
                g.enableScissor(x+4,y+26,x+cw-4,y+ch-4);
                float pitch=i==8?-85:-5;
                int baseline=(int)(y+ch*(longHair?.43:.65)+scale*1.63*Math.cos(Math.toRadians(pitch)));
                figure(g,x+cw/2,baseline,scale,YAW[i],pitch,player);
                g.disableScissor();
            }
        } finally { if(original!=null) ClientPublicStates.put(original); }
        if(++frames>=(transforming?120:18)&&!capturePending) {
            capturePending=true;
            // Run after this frame has been submitted; this captures the actual GL framebuffer.
            com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(() -> {
                Screenshot.grab(minecraft.gameDirectory,"hair-review-"+String.format("%02d",page)+"-"+preset.name().toLowerCase()+(form.equals("base")?"":"-"+form)+".png",minecraft.getMainRenderTarget(),msg->DBZenith.LOGGER.info("[hair-review] {}",msg.getString()));
                page++;frames=0;capturePending=false;
                if(page>=presets.length+7) minecraft.stop();
            });
        }
    }

    private static void figure(GuiGraphics g,int x,int y,int scale,float yaw,float pitch,LivingEntity e) {
        float by=e.yBodyRot,byo=e.yBodyRotO,yr=e.getYRot(),yro=e.yRotO,xr=e.getXRot(),xro=e.xRotO,hy=e.yHeadRot,hyo=e.yHeadRotO;
        e.yBodyRot=e.yBodyRotO=e.yRotO=e.yHeadRot=e.yHeadRotO=180+yaw;e.setYRot(180+yaw);e.setXRot(0);e.xRotO=0;
        Quaternionf pose=new Quaternionf().rotateZ((float)Math.PI),camera=new Quaternionf().rotateX((float)Math.toRadians(pitch));pose.mul(camera);
        try { InventoryScreen.renderEntityInInventory(g,x,y,scale,pose,camera,e); }
        finally { e.yBodyRot=by;e.yBodyRotO=byo;e.yRotO=yro;e.xRotO=xro;e.setYRot(yr);e.setXRot(xr);e.yHeadRot=hy;e.yHeadRotO=hyo; }
    }
    @Override public boolean isPauseScreen() { return false; }
}
