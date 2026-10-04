package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.item.TechniqueScrollItem;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.registry.ModItems;
import com.dbzenith.skill.TechniqueEffects;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.TechniqueLibrary;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Phase 3: technique library, deck, racial skills, scrolls, save migration. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TechniqueTests {
    private static final String EMPTY = "empty";

    private TechniqueTests() {}

    private static ServerPlayer as(GameTestHelper helper, Race race) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        CharacterCreation.applyRace(d, race);
        d.refill();
        return p;
    }

    /** Puts the player next to the test structure, looking along +X. */
    private static void faceAlongX(GameTestHelper helper, ServerPlayer p, double dx) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        p.teleportTo(at.getX() + 0.5 - dx, at.getY(), at.getZ() + 0.5);
        p.setYRot(-90f);
        p.setXRot(0f);
    }

    @GameTest(template = EMPTY)
    public static void deckGatesUseAndLearningCostsTp(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.HUMAN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(TechniqueHandler.use(p, Techniques.KI_BLAST) == TechniqueHandler.Result.FIRED, "starting technique works");
        helper.assertTrue(TechniqueHandler.use(p, Techniques.WAVE_BEAM) == TechniqueHandler.Result.NOT_EQUIPPED, "unlearned technique refused");
        helper.assertTrue(!TechniqueLibrary.learnWithTp(d, Techniques.WAVE_BEAM), "no TP / level, no learning");
        d.setAttribute(Attribute.STRENGTH, 200);
        d.setTrainingPoints(1000);
        helper.assertTrue(TechniqueLibrary.learnWithTp(d, Techniques.WAVE_BEAM), "learn with TP");
        helper.assertTrue(d.getTrainingPoints() == 1000 - Techniques.WAVE_BEAM.learnCost(), "TP spent");
        helper.assertTrue(d.deckView().contains("wave_beam"), "auto-equipped into a free slot");
        helper.assertTrue(TechniqueHandler.use(p, Techniques.WAVE_BEAM) == TechniqueHandler.Result.FIRED, "now usable");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void deckRequestsAreSanitized(GameTestHelper helper) {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        List<String> stored = TechniqueLibrary.setDeck(d, List.of("nope", "wave_beam", "ki_blast", "ki_blast"));
        helper.assertTrue(stored.equals(List.of("ki_blast")), "only learned, unique ids survive: " + stored);
        for (var t : Techniques.all()) d.learn(t.id());
        stored = TechniqueLibrary.setDeck(d, Techniques.all().stream().map(t -> t.id()).toList());
        helper.assertTrue(stored.size() == TechniqueLibrary.deckSlots(d), "deck trimmed to the slot count: " + stored.size());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void regenerateHealsNamekians(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.NAMEKIAN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setBody(d.getDerived().maxBody() * 0.3);
        helper.assertTrue(TechniqueHandler.use(p, Techniques.REGENERATE) == TechniqueHandler.Result.FIRED, "racial technique is equipped");
        helper.assertTrue(d.getBody() >= d.getDerived().maxBody() * 0.64, "regenerate restores 35%");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void solarFlareAndExplosiveWaveHitNearby(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.HUMAN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        faceAlongX(helper, p, 3);
        helper.assertTrue(TechniqueHandler.use(p, Techniques.SOLAR_FLARE) == TechniqueHandler.Result.FIRED, "flare fires");
        helper.assertTrue(zombie.hasEffect(MobEffects.BLINDNESS), "zombie in front is blinded");
        float hp = zombie.getHealth();
        TechniqueLibrary.learnFree(d, Techniques.EXPLOSIVE_WAVE);
        helper.assertTrue(TechniqueHandler.use(p, Techniques.EXPLOSIVE_WAVE) == TechniqueHandler.Result.FIRED, "wave fires");
        helper.assertTrue(zombie.getHealth() < hp, "explosive wave damages nearby");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void instantStepTeleportsForward(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.HUMAN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        TechniqueLibrary.learnFree(d, Techniques.INSTANT_STEP);
        p.teleportTo(p.getX(), p.getY() + 20, p.getZ()); // open air
        p.setYRot(-90f);
        p.setXRot(0f);
        double x = p.getX();
        helper.assertTrue(TechniqueHandler.use(p, Techniques.INSTANT_STEP) == TechniqueHandler.Result.FIRED, "step fires");
        helper.assertTrue(p.getX() - x > 5, "should move forward: " + (p.getX() - x));
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void candyBeamOnlyAffectsWeakMobs(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(0, 1, 0));
        helper.assertTrue(TechniqueEffects.candy(zombie, 40), "a weak zombie becomes candy");
        helper.assertTrue(zombie.isRemoved(), "zombie removed");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, zombie.getBoundingBox().inflate(2),
                i -> i.getItem().is(Items.COOKIE)).size() > 0, "cookies dropped");
        helper.assertTrue(!TechniqueEffects.candy(golem, 40), "a strong golem resists");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void energyAbsorbOpensWindow(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.ANDROID);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(TechniqueHandler.use(p, Techniques.ENERGY_ABSORB) == TechniqueHandler.Result.FIRED, "absorb fires");
        helper.assertTrue(d.getAbsorbUntil() > helper.getLevel().getGameTime(), "absorb window open");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void scrollTeachesTechnique(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.HUMAN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, TechniqueScrollItem.of(ModItems.TECHNIQUE_SCROLL.get(), Techniques.FINGER_BEAM));
        p.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        helper.assertTrue(d.knows("finger_beam"), "scroll teaches its technique");
        helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "scroll consumed");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void version1SavesKeepTheirTechniques(GameTestHelper helper) {
        PlayerData old = new PlayerData();
        old.initDefaultsIfNeeded();
        CompoundTag tag = old.save();
        tag.putInt("DataVersion", 1);
        tag.remove("learned");
        tag.remove("deck");
        PlayerData migrated = new PlayerData();
        migrated.load(tag);
        helper.assertTrue(migrated.knows("wave_beam") && migrated.knows("homing_orb"), "v1 techniques kept");
        helper.assertTrue(migrated.deckView().size() == 4, "deck filled: " + migrated.deckView());
        helper.succeed();
    }
}
