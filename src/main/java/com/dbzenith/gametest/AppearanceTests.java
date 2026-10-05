package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.HairCode.Bend;
import com.dbzenith.appearance.HairCode.Strand;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.AppearancePacket;
import com.dbzenith.transform.Forms;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** V2-D: hair codes, form hair, height and the barber's rules. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AppearanceTests {
    private static final String EMPTY = "empty";

    private AppearanceTests() {}

    @GameTest(template = EMPTY)
    public static void hairCodesRoundTripAndRejectGarbage(GameTestHelper helper) {
        for (HairCode.Preset p : HairCode.Preset.values()) {
            List<Strand> strands = HairCode.strands(p);
            helper.assertTrue(strands.size() <= HairCode.MAX_STRANDS, p + " fits the strand limit");
            helper.assertTrue(strands.equals(HairCode.decode(p.code())), p + " survives encode and decode");
            helper.assertTrue(p.code().length() <= HairCode.MAX_CODE_LENGTH, p + " fits the code length");
        }
        for (HairCode.Preset p : HairCode.Preset.values()) {
            for (com.dbzenith.transform.Form f : Forms.all()) {
                List<Strand> grown = HairCode.decode(HairCode.forForm(p.code(), f));
                helper.assertTrue(grown != null && grown.size() <= HairCode.MAX_STRANDS, p + " grows into " + f.id() + "'s hair");
            }
        }
        helper.assertTrue(HairCode.decode("").isEmpty() && "".equals(HairCode.sanitize("")), "empty code = bald");
        helper.assertTrue(HairCode.sanitize("not a hair code") == null, "garbage is rejected");
        helper.assertTrue(HairCode.sanitize(HairCode.PREFIX + "!!!") == null, "bad base64 is rejected");
        helper.assertTrue(HairCode.sanitize(HairCode.PREFIX + "AAA") == null, "a partial strand is rejected");
        helper.assertTrue(HairCode.sanitize(HairCode.PREFIX + "BwAAAA") == null, "an unknown face is rejected");
        List<Strand> many = new ArrayList<>();
        for (int i = 0; i < HairCode.MAX_STRANDS + 10; i++) many.add(new Strand(HairCode.Face.TOP, i % 8, i / 8 % 8, 0, 0, 5, 2, Bend.STRAIGHT));
        helper.assertTrue(HairCode.decode(HairCode.encode(many)).size() == HairCode.MAX_STRANDS, "encoding caps the strand count");
        Strand wild = new Strand(HairCode.Face.LEFT, 99, -4, 40, -40, 99, 0, Bend.HANG);
        helper.assertTrue(wild.u() == 7 && wild.v() == 0 && wild.yaw() == 6 && wild.pitch() == -6 && wild.length() == 16 && wild.width() == 1,
                "out-of-range strand values are clamped");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void formsGrowHairFromYourOwn(GameTestHelper helper) {
        String base = HairCode.Preset.SWEPT.code();
        List<Strand> own = HairCode.decode(base);
        List<Strand> ssj = HairCode.decode(HairCode.forForm(base, Forms.SUPER_SAIYAN));
        helper.assertTrue(ssj.size() == own.size(), "Super Saiyan keeps your strands");
        boolean stiffer = true;
        for (int i = 0; i < own.size(); i++) stiffer &= ssj.get(i).bend() == Bend.STRAIGHT && ssj.get(i).length() >= own.get(i).length();
        helper.assertTrue(stiffer, "...but stands them up, straight and longer");
        helper.assertTrue(HairCode.decode(HairCode.forForm(base, Forms.SUPER_SAIYAN_3)).size() > own.size(), "the long-haired form adds a mane");
        helper.assertTrue(HairCode.forForm(base, Forms.ULTIMATE).equals(base), "a form without hair of its own leaves yours alone");
        helper.assertTrue(HairCode.forForm("", Forms.SUPER_SAIYAN).equals(HairCode.Preset.SPIKY.code()), "bald fighters get the stock hair");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void oldWorldsKeepTheirHair(GameTestHelper helper) {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        CompoundTag tag = d.save();
        tag.remove("hairCode");
        tag.putInt("hairStyle", 1);                                            // SPIKY, saved before hair codes
        PlayerData loaded = new PlayerData();
        loaded.load(tag);
        helper.assertTrue(loaded.getHairCode().equals(HairCode.Preset.SPIKY.code()), "a legacy hairstyle becomes a hair code");
        loaded.setHairCode(HairCode.Preset.MOHAWK.code());
        PlayerData again = new PlayerData();
        again.load(loaded.save());
        helper.assertTrue(again.getHairCode().equals(HairCode.Preset.MOHAWK.code()), "hair codes persist");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void facesPackPersistAndColour(GameTestHelper helper) {
        int face = 0;
        for (com.dbzenith.appearance.FaceParts.Part p : com.dbzenith.appearance.FaceParts.Part.values()) {
            face = com.dbzenith.appearance.FaceParts.with(face, p, p.options - 1);
        }
        for (com.dbzenith.appearance.FaceParts.Part p : com.dbzenith.appearance.FaceParts.Part.values()) {
            helper.assertTrue(com.dbzenith.appearance.FaceParts.get(face, p) == p.options - 1, p + " packs");
        }
        helper.assertTrue(com.dbzenith.appearance.FaceParts.get(com.dbzenith.appearance.FaceParts.sanitize(0xFFFFFFFF),
                com.dbzenith.appearance.FaceParts.Part.EARS) == 0, "garbage from the wire falls back to the default");
        helper.assertTrue(com.dbzenith.appearance.FaceParts.with(0, com.dbzenith.appearance.FaceParts.Part.MOUTH, -1)
                == com.dbzenith.appearance.FaceParts.with(0, com.dbzenith.appearance.FaceParts.Part.MOUTH, 5), "cycling wraps");
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        int raceAura = com.dbzenith.ki.Aura.color(d);
        d.setFace(face);
        d.setHighlightColor(0xFF4040);
        d.setAuraColor(0x40D0A0);
        helper.assertTrue(com.dbzenith.ki.Aura.color(d) == 0x40D0A0 && raceAura != 0x40D0A0, "your own aura colour in base form");
        PlayerData copy = new PlayerData();
        copy.load(d.save());
        helper.assertTrue(copy.getFace() == face && copy.getHighlightColor() == 0xFF4040 && copy.getAuraColor() == 0x40D0A0, "the face is saved");
        helper.assertTrue(com.dbzenith.network.PublicStatePacket.of(1, copy).face() == face, "and shown to everyone");
        d.setAuraColor(-1);
        helper.assertTrue(com.dbzenith.ki.Aura.color(d) == raceAura, "or the race colour again");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void heightScalesTheHitbox(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(player);
        float normal = player.getBbHeight(), eye = player.getEyeHeight();
        d.setHeightPercent(115);
        player.refreshDimensions();
        helper.assertTrue(Math.abs(player.getBbHeight() - normal * 1.15f) < 0.01f, "a tall fighter is taller: " + player.getBbHeight());
        helper.assertTrue(player.getEyeHeight() > eye, "...and sees from higher up");
        helper.assertTrue(Math.abs(player.getBbWidth() - 0.6f) < 0.01f, "width is unchanged");
        d.setHeightPercent(10);
        helper.assertTrue(d.getHeightPercent() == PlayerData.MIN_HEIGHT, "height is clamped");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void barberRules(GameTestHelper helper) {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        AppearancePacket restyle = new AppearancePacket(HairCode.Preset.PONYTAIL.code(), 0xE8C860, 0x3070E0, 0xBF8656);
        helper.assertTrue(!AppearancePacket.apply(d, restyle), "no restyling before the character exists");
        d.setCharacterCreated(true);
        helper.assertTrue(AppearancePacket.apply(d, restyle), "restyle applies");
        helper.assertTrue(d.getHairCode().equals(HairCode.Preset.PONYTAIL.code()) && d.getHairColor() == 0xE8C860 && d.getSkinTone() == 0xBF8656,
                "hair, colour and skin changed");
        helper.assertTrue(!AppearancePacket.apply(d, new AppearancePacket("junk", 0, 0, -1)), "a malformed code is refused");
        helper.assertTrue(d.getHairCode().equals(HairCode.Preset.PONYTAIL.code()), "...and changes nothing");
        helper.succeed();
    }
}
