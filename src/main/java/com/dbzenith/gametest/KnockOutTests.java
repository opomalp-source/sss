package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.KnockOut;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-51: knocked out at zero health; finished by a blow, bled out, saved by a Senzu Bean, carried. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public class KnockOutTests {
    private static final String EMPTY = "empty";
    private static final int SPAWN_PROTECTION = 62;

    @GameTest(template = EMPTY, batch = "knockout", timeoutTicks = SPAWN_PROTECTION + 40)
    public static void playerKnockedOutThenSavedBySenzu(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ServerPlayer friend = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        h.runAfterDelay(SPAWN_PROTECTION, () -> {
            p.hurt(p.damageSources().generic(), 100_000f);
            h.assertTrue(p.isAlive() && p.getHealth() > 0, "a lethal blow knocks out instead of killing");
            h.assertTrue(KnockOut.isDown(p), "down on the ground");
            p.heal(1000);
            h.assertTrue(KnockOut.isDown(p) && p.getHealth() < p.getMaxHealth() * 0.2f, "no healing on your own while down");
            h.assertTrue(KnockOut.carry(friend, p) && p.getVehicle() == friend, "a friend picks them up");
            p.stopRiding();
            h.assertTrue(p.getVehicle() == friend, "the downed can't climb off");
            ItemStack senzu = new ItemStack(ModItems.SENZU_BEAN.get(), 2);
            friend.setGameMode(GameType.SURVIVAL);
            h.assertTrue(KnockOut.feed(friend, p, senzu), "a Senzu Bean is fed to them");
            h.assertTrue(!KnockOut.isDown(p) && p.getHealth() >= p.getMaxHealth() - 0.01f, "back up at full health");
            h.assertTrue(senzu.getCount() == 1, "the bean is used up");
            h.assertTrue(p.getVehicle() == null, "put down once they are up");
            TestPlayers.remove(h, p);
            TestPlayers.remove(h, friend);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "knockout", timeoutTicks = SPAWN_PROTECTION + 40)
    public static void downedFighterFinishedByABlow(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        KiFighter foe = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new net.minecraft.core.BlockPos(1, 1, 1));
        h.runAfterDelay(SPAWN_PROTECTION, () -> {
            foe.hurt(foe.damageSources().playerAttack(p), 1_000_000f);
            h.assertTrue(foe.isAlive() && KnockOut.isDown(foe), "the NPC fighter goes down instead of dying: alive=" + foe.isAlive() + " down=" + KnockOut.isDown(foe) + " hp=" + foe.getHealth() + "/" + foe.getMaxHealth());
            foe.hurt(foe.damageSources().playerAttack(p), 1f);
            h.assertTrue(!foe.isAlive() && !KnockOut.isDown(foe), "one more blow finishes them");
            TestPlayers.remove(h, p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "knockout", timeoutTicks = 120)
    public static void downedFighterBleedsOut(GameTestHelper h) {
        int was = DBZConfig.SERVER.knockOutNpcSeconds.get();
        DBZConfig.SERVER.knockOutNpcSeconds.set(1);
        KiFighter foe = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new net.minecraft.core.BlockPos(1, 1, 1));
        foe.hurt(foe.damageSources().generic(), 1_000_000f);
        DBZConfig.SERVER.knockOutNpcSeconds.set(was);
        h.assertTrue(KnockOut.isDown(foe), "down");
        h.runAfterDelay(40, () -> {
            h.assertTrue(!foe.isAlive(), "bled out after its time");
            h.succeed();
        });
    }
}
