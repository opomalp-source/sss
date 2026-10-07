package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.CombatMoves;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.stats.Attribute;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-6: combat v3 moves. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatV3Tests {
    private static final String EMPTY = "empty";

    private CombatV3Tests() {}

    private static ServerPlayer fighter(GameTestHelper helper, Vec3 at) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setAttribute(Attribute.STRENGTH, 300);
        d.setAttribute(Attribute.CONSTITUTION, 300);
        d.recomputeIfStale();
        d.refill();
        p.teleportTo(at.x, at.y, at.z);
        return p;
    }

    private static Zombie zombie(GameTestHelper helper, BlockPos rel) {
        Zombie z = helper.spawn(EntityType.ZOMBIE, rel);
        z.setNoAi(true);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        z.setHealth(1000);
        return z;
    }

    @GameTest(template = EMPTY)
    public static void heaviesLaunchAndChase(GameTestHelper helper) {
        Zombie z = zombie(helper, new BlockPos(3, 2, 1));
        Vec3 base = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        ServerPlayer a = fighter(helper, base);
        PlayerData ad = ModCapabilities.getOrThrow(a);
        long now = helper.getLevel().getGameTime();
        CombatMoves.launched(a, ad, z, now);
        helper.assertTrue(ad.combat().chaseReadyUntil > now, "a launch offers a chase");
        z.teleportTo(z.getX(), z.getY() + 6, z.getZ() + 6);
        helper.assertTrue(com.dbzenith.combat.engine.Evasion.dashKey(a, ad, 0, 0), "the dash key chases");
        helper.assertTrue(a.distanceTo(z) < 3 && ad.combat().chaseCount == 1, "right into its path, got " + a.distanceTo(z));
        helper.assertTrue(!com.dbzenith.combat.engine.Evasion.dashKey(a, ad, 0, 0), "one chase per launch: the next dash is just a dash");
        for (int i = 0; i < 3; i++) {
            ad.refill();
            CombatMoves.launched(a, ad, z, now + 1);
            com.dbzenith.combat.engine.Evasion.dashKey(a, ad, 0, 0);
        }
        helper.assertTrue(ad.combat().chaseCount == 3, "three chases a combo, got " + ad.combat().chaseCount);
        z.discard();
        TestPlayers.remove(helper, a);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sweepBreakerAndRevenge(GameTestHelper helper) {
        Zombie z = zombie(helper, new BlockPos(1, 2, 3));
        Vec3 base = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        ServerPlayer a = fighter(helper, base);
        PlayerData ad = ModCapabilities.getOrThrow(a);
        a.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
        long now = helper.getLevel().getGameTime();
        float hp;
        CombatMoves.knockDown(z, now);
        helper.assertTrue(CombatMoves.isDowned(z, now), "floored");

        for (int i = 0; i < 2; i++) {                                           // Burst: Dash while caught in a combo
            a.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 40, 0));
            ad.refill();
            helper.assertTrue(com.dbzenith.combat.engine.Evasion.dashKey(a, ad, 0, 0) && !ModEffects.isStunned(a), "a Burst frees you");
        }
        helper.assertTrue(ad.combat().breakerCharges == 0, "two charges: " + ad.combat().breakerCharges);
        a.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 40, 0));
        helper.assertTrue(com.dbzenith.combat.engine.Evasion.dashKey(a, ad, 0, 0) && ModEffects.isStunned(a), "then none");
        z.discard();
        TestPlayers.remove(helper, a);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void recoveriesAndDodges(GameTestHelper helper) {
        Vec3 base = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        ServerPlayer a = fighter(helper, base);
        PlayerData ad = ModCapabilities.getOrThrow(a);
        long now = helper.getLevel().getGameTime();
        CombatMoves.knockDown(a, now);
        helper.assertTrue(com.dbzenith.combat.engine.Evasion.dashKey(a, ad, 0, 0) && !CombatMoves.isDowned(a, now) && !ad.combat().downedFlag, "roll up from the floor");

        a.teleportTo(base.x, base.y + 6, base.z);
        a.setOnGround(false);
        com.dbzenith.combat.engine.CombatEngine.of(a).markJuggled();
        double stamina = ad.getStamina();
        helper.assertTrue(com.dbzenith.combat.engine.Evasion.dashKey(a, ad, 0, 0) && ad.getStamina() < stamina, "snap recovery in the air");

        a.teleportTo(base.x, base.y, base.z);
        a.setOnGround(true);
        ad.setGuarding(true);
        helper.assertTrue(com.dbzenith.combat.engine.Evasion.dashKey(a, ad, 0, 0) && ad.getDashEvadeUntil() >= now + 10, "spot dodge while guarding");
        TestPlayers.remove(helper, a);
        helper.succeed();
    }
}
