package com.dbzenith.client.screen;
import com.dbzenith.client.ui.*;
import com.dbzenith.galaxy.*;
import com.dbzenith.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
public final class IconicScreen extends Screen {
    private final Screen parent;
    public IconicScreen(Screen parent){super(Component.literal("Known star charts"));this.parent=parent;}
    @Override protected void init(){String[] names={"Earth","Namek","Planet Vegeta","King Kai's Planet","Frieza Planet 79","Beerus's Planet"};var ids=IconicSystems.all();int top=Math.max(24,height/2-90);
        for(int i=0;i<ids.size();i++){var id=ids.get(i);addRenderableWidget(ThemedButton.of(Component.literal(names[i]),b->ModNetwork.sendToServer(new GalaxyPackets.Request(0,10,id.toString(),0,"",0))).bounds(width/2-110,top+24+i*20,220,18).build());}
        addRenderableWidget(ThemedButton.of(Component.literal("Back"),b->onClose()).bounds(width/2-40,top+149,80,18).build());}
    @Override public void render(GuiGraphics g,int x,int y,float pt){DbzTheme.screenBackground(g,width,height);g.drawCenteredString(font,title,width/2,Math.max(24,height/2-90)+5,0xFFFFCF8E);super.render(g,x,y,pt);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
