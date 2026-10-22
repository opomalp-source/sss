package com.dbzenith.social;
import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.screen.MenuScreen;
import com.dbzenith.client.ui.*;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds tabs to the existing baseline menu without replacing its newer implementation. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public final class SocialClient {
    public static void receive(CompoundTag tag){
        if(tag==null||tag.equals(SocialState.snapshot))return;SocialState.snapshot=tag;SocialState.clanNames.clear();
        for(Tag row:tag.getList("Players",Tag.TAG_COMPOUND)){var p=(CompoundTag)row;if(p.hasUUID("Id"))SocialState.clanNames.put(p.getUUID("Id"),p.getString("Clan"));}
        SocialState.revision++;
        var mc=Minecraft.getInstance();if(mc.level!=null)mc.level.players().forEach(p->p.refreshDisplayName());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){SocialState.clear();}
    @SubscribeEvent public static void init(ScreenEvent.Init.Post e){
        if(!(e.getScreen() instanceof MenuScreen screen))return;
        var mc=Minecraft.getInstance();int w=Math.min(screen.width,600),h=Math.min(screen.height,340),x=(screen.width-w)/2+8,y=(screen.height-h)/2+22;
        java.util.List<UiButton> pages=new java.util.ArrayList<>();
        for(var widget:e.getListenersList())if(widget instanceof UiButton button&&button.getY()==y)pages.add(button);
        int tab=(w-16)/(pages.size()+2),index=0;                                 // the menu's pages, then Party and Clans
        for(UiButton button:pages){button.setX(x+index*tab);button.setWidth(tab-2);if(screen instanceof SocialScreen)button.selected(false);index++;}
        for(boolean clan:new boolean[]{false,true}){
            final boolean mode=clan;var button=UiButton.of(UiButton.Style.TAB,Component.literal(clan?"Clans":"Party"),x+(pages.size()+(clan?1:0))*tab,y,tab-2,16,b->mc.setScreen(new SocialScreen(mode)));
            button.selected(screen instanceof SocialScreen s&&s.clan==clan);e.addListener(button);
        }
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post e){
        if(!(e.getScreen() instanceof MenuScreen screen))return;
        var mc=Minecraft.getInstance();int w=Math.min(screen.width,600),h=Math.min(screen.height,340),ox=(screen.width-w)/2,oy=(screen.height-h)/2;
        Component tp=Component.translatable("screen.dbzenith.tp",String.format("%,d",ClientPlayerData.get().getTrainingPoints()));
        String level="Level "+StatCalculator.level(ClientPlayerData.get());
        float x=ox+w-22-mc.font.width(tp)*.8f-mc.font.width(level)*.8f;
        Ui.text(e.getGuiGraphics(),mc.font,level,x,oy+9.5f,Ui.GOLD,.8f);
    }
}
