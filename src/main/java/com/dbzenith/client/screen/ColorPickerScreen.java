package com.dbzenith.client.screen;

import com.dbzenith.appearance.ColorValues;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.function.IntConsumer;

/** Full RGB picker: saturation/value square, hue rail, editable hex, presets and explicit Apply/Cancel. */
public final class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final IntConsumer apply;
    private final int[] presets;
    private final Component defaultLabel;
    private final boolean allowDefault;
    private int rgb;
    private boolean useDefault, updating;
    private float hue,saturation,value;
    private int x,y,w,h,sx,sy,sw,sh,hy,ey,py,drag;
    private EditBox hex;
    private UiButton done;
    public ColorPickerScreen(Screen parent,int initial,int[] presets,Component defaultLabel,IntConsumer apply) {
        super(Component.translatable("color.dbzenith.title"));
        this.parent=parent;this.apply=apply;this.presets=presets.clone();this.defaultLabel=defaultLabel;
        allowDefault=java.util.Arrays.stream(presets).anyMatch(c->c<0);
        useDefault=allowDefault&&initial<0;
        rgb=initial<0?0xFFFFFF:initial&0xFFFFFF;
        readHsv();
    }
    private void readHsv() {float[] hsv=ColorValues.hsv(rgb);hue=hsv[0];saturation=hsv[1];value=hsv[2];}
    @Override protected void init() {
        w=Math.min(300,width-12);h=Math.min(300,height-8);x=(width-w)/2;y=(height-h)/2;
        sx=x+14;sy=y+30;sw=w-28;sh=h-142;hy=sy+sh+10;ey=hy+22;py=ey+27;
        hex=new EditBox(font,sx+30,ey,sw-30,18,Component.translatable("color.dbzenith.hex"));
        hex.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("color.dbzenith.hex")));
        hex.setMaxLength(7);hex.setFilter(s->s.matches("#?[0-9a-fA-F]{0,6}"));
        hex.setValue(ColorValues.hex(rgb));
        hex.setResponder(s->{if(updating)return;useDefault=false;var parsed=ColorValues.parse(s);if(parsed.isPresent()){rgb=parsed.getAsInt();useDefault=false;readHsv();}validate();});
        addRenderableWidget(hex);
        int bw=(sw-8)/3;
        if(allowDefault)addRenderableWidget(UiButton.of(UiButton.Style.SECONDARY,defaultLabel,sx,y+h-28,bw,18,b->{useDefault=true;validate();}));
        done=addRenderableWidget(UiButton.of(UiButton.Style.PRIMARY,Component.translatable("color.dbzenith.apply"),sx+bw+4,y+h-28,bw,18,b->commit()));
        addRenderableWidget(UiButton.of(UiButton.Style.GHOST,Component.translatable("gui.cancel"),sx+2*(bw+4),y+h-28,bw,18,b->onClose()));
        validate();
    }
    private void validate(){if(done!=null)done.active=useDefault||ColorValues.parse(hex.getValue()).isPresent();hex.setTextColor(useDefault||ColorValues.parse(hex.getValue()).isPresent()?0xFFFFFF:0xFF6464);}
    private void refreshHex(){updating=true;hex.setValue(ColorValues.hex(rgb));updating=false;validate();}
    public void commit(){if(done!=null&&done.active){apply.accept(useDefault?-1:rgb);minecraft.setScreen(parent);}}
    /** Dev automation goes through the real editor validation and apply path. */
    public void inputHex(String text){hex.setValue(text);}
    private void point(double mx,double my){
        if(drag==1){saturation=Mth.clamp((float)(mx-sx)/(sw-1),0,1);value=1-Mth.clamp((float)(my-sy)/(sh-1),0,1);}
        else if(drag==2)hue=Mth.clamp((float)(mx-sx)/(sw-1),0,1);
        rgb=ColorValues.rgb(hue,saturation,value);useDefault=false;refreshHex();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0){
            if(mx>=sx&&mx<sx+sw&&my>=sy&&my<sy+sh){drag=1;setDragging(true);setFocused(null);point(mx,my);return true;}
            if(mx>=sx&&mx<sx+sw&&my>=hy-3&&my<hy+13){drag=2;setDragging(true);setFocused(null);point(mx,my);return true;}
            for(int i=0;i<presets.length;i++)if(mx>=sx+i*16&&mx<sx+i*16+12&&my>=py&&my<py+12){
                int c=presets[i];useDefault=c<0;if(c>=0){rgb=c;readHsv();}refreshHex();return true;
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(button==0&&drag>0){point(mx,my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){drag=0;setDragging(false);return super.mouseReleased(mx,my,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257||key==335){commit();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        Ui.backdrop(g,width,height,x+w*.5f,y+h*.5f,rgb);Ui.card(g,x,y,w,h);
        Ui.text(g,font,title,sx,y+10,Ui.GOLD,.9f);
        int pure=ColorValues.rgb(hue,1,1);
        for(int i=0;i<sw;i++){
            float s=i/(float)(sw-1);int r=Math.round(255+( (pure>>16&255)-255)*s),gr=Math.round(255+((pure>>8&255)-255)*s),b=Math.round(255+((pure&255)-255)*s);
            g.fillGradient(sx+i,sy,sx+i+1,sy+sh,0xFF000000|r<<16|gr<<8|b,0xFF000000);
            g.fill(sx+i,hy,sx+i+1,hy+10,0xFF000000|ColorValues.rgb(i/(float)(sw-1),1,1));
        }
        int px=sx+Math.round(saturation*(sw-1)),pv=sy+Math.round((1-value)*(sh-1));
        for(int a=-5;a<=5;a++)for(int b=-5;b<=5;b++){int d=a*a+b*b;if(d>=10&&d<=25)g.fill(px+a,pv+b,px+a+1,pv+b+1,d>=17?0xFF101018:0xFFFFFFFF);}
        int hx=sx+Math.round(hue*(sw-1));g.fill(hx-2,hy-2,hx+3,hy+12,0xFFFFFFFF);g.fill(hx-1,hy,hx+2,hy+10,0xFF000000|ColorValues.rgb(hue,1,1));
        Ui.swatch(g,sx,ey,18,useDefault?-1:rgb,false,false);
        for(int i=0;i<presets.length;i++)Ui.swatch(g,sx+i*16,py,12,presets[i],useDefault?presets[i]<0:presets[i]==rgb,false);
        super.render(g,mx,my,partial);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
