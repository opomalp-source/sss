package com.dbzenith.appearance;

import java.util.Locale;
import java.util.OptionalInt;

/** Opaque 24-bit RGB. -1 is reserved by appearance fields for their default/none choice. */
public final class ColorValues {
    private ColorValues() {}
    public static String hex(int rgb) { return String.format(Locale.ROOT,"#%06X",rgb & 0xFFFFFF); }
    public static OptionalInt parse(String text) {
        if (text == null) return OptionalInt.empty();
        String s=text.trim(); if(s.startsWith("#"))s=s.substring(1);
        if(!s.matches("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}"))return OptionalInt.empty();
        if(s.length()==3)s=""+s.charAt(0)+s.charAt(0)+s.charAt(1)+s.charAt(1)+s.charAt(2)+s.charAt(2);
        return OptionalInt.of(Integer.parseInt(s,16));
    }
    public static int rgb(float hue,float saturation,float value) {
        hue=Float.isFinite(hue)?hue-(float)Math.floor(hue):0;
        saturation=clamp(saturation); value=clamp(value);
        float h=hue*6, f=h-(int)h, p=value*(1-saturation), q=value*(1-f*saturation), t=value*(1-(1-f)*saturation);
        float r,g,b;
        switch((int)h%6) {
            case 0 -> {r=value;g=t;b=p;} case 1 -> {r=q;g=value;b=p;}
            case 2 -> {r=p;g=value;b=t;} case 3 -> {r=p;g=q;b=value;}
            case 4 -> {r=t;g=p;b=value;} default -> {r=value;g=p;b=q;}
        }
        return Math.round(r*255)<<16 | Math.round(g*255)<<8 | Math.round(b*255);
    }
    public static float[] hsv(int rgb) {
        float r=(rgb>>16&255)/255f,g=(rgb>>8&255)/255f,b=(rgb&255)/255f;
        float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min,h=0;
        if(d>0) {if(max==r)h=((g-b)/d)%6;else if(max==g)h=(b-r)/d+2;else h=(r-g)/d+4;h/=6;if(h<0)h++;}
        return new float[]{h,max==0?0:d/max,max};
    }
    private static float clamp(float f) {return Float.isFinite(f)?Math.max(0,Math.min(1,f)):0;}
}
