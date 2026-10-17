package com.dbzenith.client.ui;

import com.dbzenith.appearance.ColorValues;
import com.dbzenith.client.screen.ColorPickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** The same accessible current-color button on every customization page. */
public final class ColorControl extends UiButton {
    private final IntSupplier get;
    private final Component defaultLabel;
    public ColorControl(Screen parent,int x,int y,int width,IntSupplier get,IntConsumer set,int[] presets) {
        this(parent,x,y,width,get,set,presets,Component.translatable("color.dbzenith.default"));
    }
    public ColorControl(Screen parent,int x,int y,int width,IntSupplier get,IntConsumer set,int[] presets,Component defaultLabel) {
        super(x,y,width,14,Component.empty(),Style.SECONDARY,b -> Minecraft.getInstance().setScreen(
                new ColorPickerScreen(parent,get.getAsInt(),presets,defaultLabel,set)));
        this.get=get;this.defaultLabel=defaultLabel;
        tip(Component.translatable("color.dbzenith.open"));
    }
    public int currentColor() { return get.getAsInt(); }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
        int c=get.getAsInt();
        setMessage(c<0?defaultLabel:Component.literal(ColorValues.hex(c)));
        super.renderWidget(g,mx,my,partial);
        Ui.swatch(g,getX()+4,getY()+3,8,c,c<0,false);
    }
}
