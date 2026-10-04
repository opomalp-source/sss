package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.stats.Race;
import com.dbzenith.world.Family;
import com.dbzenith.world.Needs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 5 slice 5: thirst, temperature, family, scars and tattoos. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeedsTests {
    private static final String EMPTY = "empty";

    private NeedsTests() {}

    private static ServerPlayer survivor(GameTestHelper helper, Race race) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        CharacterCreation.applyRace(ModCapabilities.getOrThrow(p), race);
        p.teleportTo(p.getX(), p.getY() + 50, p.getZ()); // dry
        return p;
    }

    @GameTest(template = EMPTY)
    public static void thirstDrainsAndDrinkingRefills(GameTestHelper helper) {
        ServerPlayer p = survivor(helper, Race.HUMAN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        for (int i = 0; i < 120; i++) Needs.tick(p, d);
        helper.assertTrue(d.getThirst() < 100, "thirst drains over time: " + d.getThirst());
        d.setThirst(10);
        helper.assertTrue(Needs.staminaRegenMultiplier(d) == 0.5, "thirsty fighters recover stamina slowly");
        ForgeEventFactory.onItemUseFinish(p, new ItemStack(Items.POTION), 0, new ItemStack(Items.GLASS_BOTTLE));
        helper.assertTrue(d.getThirst() == 50, "a water bottle quenches 40: " + d.getThirst());
        d.setThirst(0);
        d.setBody(d.getDerived().maxBody() * 0.11);
        for (int i = 0; i < 10; i++) Needs.tick(p, d);
        helper.assertTrue(d.getBody() >= d.getDerived().maxBody() * 0.1 - 1e-6, "dehydration never takes you below 10%");
        ServerPlayer android = survivor(helper, Race.ANDROID);
        PlayerData a = ModCapabilities.getOrThrow(android);
        a.setThirst(30);
        Needs.tick(android, a);
        helper.assertTrue(a.getThirst() == 100, "androids never thirst");
        TestPlayers.remove(helper, p);
        TestPlayers.remove(helper, android);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void heatAndColdSlowRecovery(GameTestHelper helper) {
        ServerPlayer p = survivor(helper, Race.HUMAN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setThirst(100);
        d.setTemperature(1);
        helper.assertTrue(Needs.staminaRegenMultiplier(d) == 0.5, "too hot");
        d.setTemperature(0);
        helper.assertTrue(Needs.staminaRegenMultiplier(d) == 1.0, "comfortable");
        ServerPlayer frost = survivor(helper, Race.FROST_DEMON);
        PlayerData f = ModCapabilities.getOrThrow(frost);
        f.setTemperature(-1);
        Needs.tick(frost, f);
        helper.assertTrue(f.getTemperature() == 0, "Frost Demons ignore the weather");
        TestPlayers.remove(helper, p);
        TestPlayers.remove(helper, frost);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void partnersTrainBetterTogether(GameTestHelper helper) {
        ServerPlayer a = survivor(helper, Race.HUMAN);
        ServerPlayer b = survivor(helper, Race.SAIYAN);
        b.teleportTo(a.getX() + 3, a.getY(), a.getZ());
        PlayerData ad = ModCapabilities.getOrThrow(a);
        PlayerData bd = ModCapabilities.getOrThrow(b);
        helper.assertTrue(!Family.accept(b), "nothing to accept yet");
        helper.assertTrue(Family.propose(a, b), "ring offered");
        helper.assertTrue(Family.accept(b), "accepted");
        helper.assertTrue(ad.getPartnerId().equals(b.getStringUUID()) && bd.getPartnerId().equals(a.getStringUUID()), "partners");
        Family.tick(a, ad);
        helper.assertTrue(ad.isNearPartner() && Family.tpMultiplier(ad) > 1.0, "training together pays");
        helper.assertTrue(!Family.propose(a, b), "already taken");
        helper.assertTrue(Family.leave(b), "left");
        helper.assertTrue(ad.getPartnerId().isEmpty() && bd.getPartnerId().isEmpty() && !ad.isNearPartner(), "both single again");
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void questNpcsGetBuildings(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        net.minecraft.core.BlockPos far = new net.minecraft.core.BlockPos(30_000, 0, 30_000); // away from the other tests
        com.dbzenith.npc.QuestNpcPlacement.buildWithNpc(level, com.dbzenith.npc.NpcStructures.Kind.DOJO, far);
        var masters = level.getEntitiesOfClass(com.dbzenith.npc.QuestGiverEntity.class, new net.minecraft.world.phys.AABB(far).inflate(8, 400, 8));
        helper.assertTrue(masters.size() == 1, "the master stands in the dojo");
        var m = masters.get(0);
        helper.assertTrue(level.getBlockState(m.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS), "on the dojo floor");
        helper.assertTrue(level.getBlockState(m.blockPosition().offset(2, 0, 2)).is(com.dbzenith.registry.ModBlocks.PUNCHING_BAG.get()), "with a punching bag");
        helper.assertTrue(!level.getBlockState(m.blockPosition().above(4)).isAir(), "under a roof");
        m.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void scarsAndTattoosAreVisible(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setCosmetics(2, 3);
        PublicStatePacket s = PublicStatePacket.of(p.getId(), d);
        helper.assertTrue(s.scar() == 2 && s.tattoo() == 3, "everyone sees the marks");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
