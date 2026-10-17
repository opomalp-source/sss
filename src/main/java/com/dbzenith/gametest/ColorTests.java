package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.appearance.ColorValues;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.Aura;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.skill.CustomTechniques;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColorTests {
    @GameTest(template="empty")
    public static void hexAndHsvCoverFullRgb(GameTestHelper h) {
        h.assertTrue(ColorValues.parse("#AD2F17").orElseThrow()==0xAD2F17,"reference hex preserved");
        h.assertTrue(ColorValues.parse("  13b79d ").orElseThrow()==0x13B79D,"paste without hash/case normalization");
        h.assertTrue(ColorValues.parse("#0f8").orElseThrow()==0x00FF88,"short hex expanded");
        for(String bad:new String[]{"", "#", "12", "GGGGGG", "12345", "#FFFFFFFF", "-1"})
            h.assertTrue(ColorValues.parse(bad).isEmpty(),"reject incomplete/invalid/alpha: "+bad);
        int[] values={0,0xFFFFFF,0xFF0000,0x00FF00,0x0000FF,0x010203,0xAD2F17,0x13B79D};
        for(int c:values)h.assertTrue(ColorValues.parse(ColorValues.hex(c)).orElseThrow()==c,"lossless hex including black/white");
        var random=new java.util.Random(150);
        for(int i=0;i<4096;i++){
            int c=random.nextInt(1<<24);float[] hsv=ColorValues.hsv(c);
            h.assertTrue(ColorValues.rgb(hsv[0],hsv[1],hsv[2])==c,"HSV roundtrip "+ColorValues.hex(c));
        }
        h.assertTrue(ColorValues.rgb(1,1,1)==0xFF0000,"hue rail wraps to red");
        h.assertTrue(ColorValues.rgb(Float.NaN,Float.NaN,Float.NaN)==0,"nonfinite input bounded");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void arbitraryAppearanceColorsSaveAndSync(GameTestHelper h) {
        var data=new PlayerData();data.initDefaultsIfNeeded();
        data.setHairColor(0xAD2F17);data.setEyeColor(0x13B79D);data.setSkinTone(0xBCA381);
        data.setHighlightColor(0x0745E1);data.setAuraColor(0x00ABCD);data.setRaceCustom(2,0x698AE1,0xD35C91,0x023456);
        var spec=new CustomTechniques.Spec("Custom hue",CustomTechniques.Kind.BLAST,1,0,0x8723B9);
        data.setCustomSpec(0,spec);
        var copy=new PlayerData();copy.load(data.save());
        h.assertTrue(copy.getHairColor()==0xAD2F17 && copy.getEyeColor()==0x13B79D && copy.getSkinTone()==0xBCA381,"hair/eyes/skin exact");
        h.assertTrue(copy.getHighlightColor()==0x0745E1 && Aura.color(copy)==0x00ABCD,"highlight and actual base aura exact");
        h.assertTrue(copy.getRaceSkinColor()==0x698AE1 && copy.getRaceMarkColor()==0xD35C91 && copy.getRacePartColor()==0x023456,"race channels exact");
        h.assertTrue(copy.getCustomSpec(0).color()==0x8723B9,"ki design color persists");
        var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            PublicStatePacket.encode(PublicStatePacket.of(123,copy),buf);var state=PublicStatePacket.decode(buf);
            h.assertTrue(state.hairColor()==0xAD2F17 && state.eyeColor()==0x13B79D && state.auraColor()==0x00ABCD && state.raceMark()==0xD35C91,"tracker packet keeps RGB precision");
        }finally{buf.release();}
        copy.setEyeColor(0);copy.setAuraColor(0);h.assertTrue(copy.getEyeColor()==0 && Aura.color(copy)==0,"black is a real color, not default");
        copy.setEyeColor(-1);copy.setAuraColor(-1);copy.setHighlightColor(-1);copy.setRaceCustom(0,-1,-1,-1);
        h.assertTrue(copy.getEyeColor()==-1 && copy.getAuraColor()==-1 && copy.getHighlightColor()==-1 && copy.getRacePartColor()==-1,"default/none sentinels retained");
        h.succeed();
    }
}
