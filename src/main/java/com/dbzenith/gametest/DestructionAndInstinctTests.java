package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.skill.Hakai;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.GodKi;
import com.dbzenith.transform.UltraInstinct;
import com.dbzenith.world.BeerusPlanetBuilder;
import com.dbzenith.world.Planet;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-17b: Hakai erases the weak, Ultra Instinct dodges on its own, and Beerus's Planet is only for those with godly ki. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DestructionAndInstinctTests {
    private static final String EMPTY = "empty";

    private DestructionAndInstinctTests() {}

    @GameTest(template = EMPTY)
    public static void hakaiErasesWhatIsWeaker(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.CREATIVE);
        net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)));   // away from spawn, where other tests' players stand
        p.teleportTo(at.x, at.y, at.z);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(com.dbzenith.skill.TechniqueLibrary.learnProblem(d, Techniques.HAKAI) != null, "Hakai cannot be bought with TP");
        Zombie z = EntityType.ZOMBIE.create(p.level());
        z.moveTo(p.getX() + 4, p.getY(), p.getZ(), 0, 0);
        z.setNoAi(true);
        p.level().addFreshEntity(z);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
        helper.assertTrue(Hakai.erases(d, z), "a zombie is far weaker");
        var r = TechniqueHandler.use(p, Techniques.HAKAI, true);
        helper.assertTrue(r == TechniqueHandler.Result.FIRED, "cast: " + r);
        helper.assertTrue(!z.isAlive(), "erased");
        p.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition().add(0, 5, 0));
        d.setCooldown(Techniques.HAKAI.id(), 0);
        helper.assertTrue(TechniqueHandler.use(p, Techniques.HAKAI, true) == TechniqueHandler.Result.INVALID, "nothing in reach: nothing spent");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void ultraInstinctDodgesOnItsOwn(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper), foe = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(UltraInstinct.problem(d, UltraInstinct.SIGN) != null, "locked until Whis teaches it");
        d.setFlag(UltraInstinct.SIGN_FLAG, true);
        d.setFlag(FormHandler.UNLOCK_FLAG + UltraInstinct.SIGN_ID, true);
        UltraInstinct.enter(p, d, UltraInstinct.SIGN);
        d.setStamina(d.getDerived().maxStamina());
        helper.assertTrue(UltraInstinct.evadeChance(d) >= 0.25 && UltraInstinct.evadeChance(d) < 0.5, "the Sign: a quarter to a half");
        double stamina = d.getStamina();
        helper.assertTrue(UltraInstinct.evade(p, d, foe, 0.1), "a low roll slips past");
        helper.assertTrue(d.getStamina() < stamina, "a dodge costs a little stamina");
        helper.assertTrue(!UltraInstinct.evade(p, d, foe, 0.9), "a high roll lands");
        UltraInstinct.enter(p, d, UltraInstinct.MASTERED);
        helper.assertTrue(UltraInstinct.evadeChance(d) >= 0.5, "Mastered: half or more");
        UltraInstinct.stop(p, d, true);
        helper.assertTrue(UltraInstinct.evadeChance(d) == 0, "nothing outside it");
        TestPlayers.remove(helper, p);
        TestPlayers.remove(helper, foe);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void ultraInstinctIsATechniqueOverTheForm(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setRace(com.dbzenith.stats.Race.NAMEKIAN);                              // anyone can learn it
        d.setFlag(UltraInstinct.SIGN_FLAG, true);
        d.setFlag(FormHandler.UNLOCK_FLAG + UltraInstinct.SIGN_ID, true);
        double base = com.dbzenith.transform.FormMath.attributeMultiplier(d, com.dbzenith.stats.Attribute.DEXTERITY);
        UltraInstinct.enter(p, d, UltraInstinct.SIGN);
        helper.assertTrue(d.getFormId().equals(Forms.BASE.id()), "the form stays what it was");
        double in = com.dbzenith.transform.FormMath.attributeMultiplier(d, com.dbzenith.stats.Attribute.DEXTERITY);
        helper.assertTrue(in > base * 5, "lifted to its own power from base: " + base + " -> " + in);
        helper.assertTrue(Forms.byId(UltraInstinct.SIGN_ID) == Forms.BASE && !Forms.exists(UltraInstinct.SIGN_ID), "no longer a form");
        d.setKaiokenStage(0);
        d.setKi(0);
        UltraInstinct.tick(p, d, 1);
        helper.assertTrue(!UltraInstinct.isIn(d), "out of ki: it gives out");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void beerusPlanetNeedsGodlyKi(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(!Planet.travel(p, Planet.BEERUS_PLANET), "no course without godly ki");
        d.setFlag(GodKi.FLAG, true);
        helper.assertTrue(Planet.travel(p, Planet.BEERUS_PLANET), "with it, the pod finds the way");
        helper.assertTrue(Planet.of(p.level()) == Planet.BEERUS_PLANET, "arrived");
        helper.assertTrue(p.level().getBlockState(new net.minecraft.core.BlockPos(3, BeerusPlanetBuilder.TOP, BeerusPlanetBuilder.TEMPLE_Z + 3))
                .is(net.minecraft.world.level.block.Blocks.QUARTZ_PILLAR), "the temple stands, its pavilion on top");
        AABB temple = new AABB(-30, -10, BeerusPlanetBuilder.TEMPLE_Z - 30, 30, 60, BeerusPlanetBuilder.TEMPLE_Z + 30);
        helper.succeedWhen(() -> {                                                    // once the temple's chunk has its people loaded
            helper.assertTrue(!p.level().getEntities(ModNpcs.BEERUS.get(), temple, e -> true).isEmpty(), "Beerus is home");
            helper.assertTrue(!p.level().getEntities(ModNpcs.WHIS.get(), temple, e -> true).isEmpty(), "so is Whis");
            d.setCooldown("space_travel", 0);
            Planet.travel(p, Planet.EARTH);
            TestPlayers.remove(helper, p);
        });
    }
}
