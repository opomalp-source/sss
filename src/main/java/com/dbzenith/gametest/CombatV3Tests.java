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
    public static void movesPickTheRightMultiplier(GameTestHelper helper) {
        helper.assertTrue(CombatMoves.direction(1, 0) == CombatMoves.DIR_FORWARD && CombatMoves.direction(-1, 0) == CombatMoves.DIR_BACK
                && CombatMoves.direction(0, 1) == CombatMoves.DIR_LEFT && CombatMoves.direction(0, -1) == CombatMoves.DIR_RIGHT
                && CombatMoves.direction(0.1f, 0.1f) == CombatMoves.DIR_NEUTRAL, "movement picks the heavy");
        Vec3 base = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        ServerPlayer a = fighter(helper, base);
        ServerPlayer b = fighter(helper, base.add(1.5, 0, 0));
        PlayerData ad = ModCapabilities.getOrThrow(a), bd = ModCapabilities.getOrThrow(b);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(CombatMoves.beforeMelee(a, ad, b, now) == 1.0, "a plain blow");
        helper.assertTrue(CombatMoves.beforeMelee(b, bd, a, now + 2) == 0.0, "both swung at once: a clash");
        ad.combat().lastDashTick = now + 10;
        helper.assertTrue(Math.abs(CombatMoves.beforeMelee(a, ad, b, now + 12) - 1.5) < 1e-9, "a blow off a dash is a Z-hit");
        ad.combat().lastDashTick = Long.MIN_VALUE / 2;
        CombatMoves.knockDown(b, now + 20);
        helper.assertTrue(Math.abs(CombatMoves.beforeMelee(a, ad, b, now + 30) - 0.5) < 1e-9, "the downed take half");
        helper.assertTrue(bd.combat().downedFlag, "and show it");
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void heaviesLaunchAndChase(GameTestHelper helper) {
        Zombie z = zombie(helper, new BlockPos(3, 2, 1));
        Vec3 base = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        ServerPlayer a = fighter(helper, base);
        PlayerData ad = ModCapabilities.getOrThrow(a);
        long now = helper.getLevel().getGameTime();
        CombatMoves.afterMelee(a, ad, z, true, now);
        helper.assertTrue(ad.combat().chaseReadyUntil > now, "a launch offers a chase");
        z.teleportTo(z.getX(), z.getY() + 6, z.getZ() + 6);
        helper.assertTrue(CombatMoves.dashKey(a, ad, 0, 0), "the dash key chases");
        helper.assertTrue(a.distanceTo(z) < 3 && ad.combat().chaseCount == 1, "right into its path, got " + a.distanceTo(z));
        helper.assertTrue(!CombatMoves.dashKey(a, ad, 0, 0), "one chase per launch: the next dash is just a dash");
        for (int i = 0; i < 3; i++) {
            ad.refill();
            CombatMoves.afterMelee(a, ad, z, true, now + 1);
            CombatMoves.dashKey(a, ad, 0, 0);
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
        float hp = z.getHealth();
        helper.assertTrue(CombatMoves.sweep(a, ad) == 1 && z.getHealth() < hp, "the sweep connects");
        helper.assertTrue(CombatMoves.isDowned(z, now), "and floors it");

        ad.setLastFoeHitTick(now);
        a.setShiftKeyDown(true);
        helper.assertTrue(CombatMoves.dashKey(a, ad, 0, 0) && ad.combat().breakerCharges == 1, "Breaker Wave spends a charge");
        CombatMoves.dashKey(a, ad, 0, 0);
        helper.assertTrue(ad.combat().breakerCharges == 0, "two charges");
        helper.assertTrue(CombatMoves.dashKey(a, ad, 0, 0) && ad.combat().breakerCharges == 0, "then none");
        a.setShiftKeyDown(false);

        ad.combat().lastAttackerId = z.getId();
        a.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 40, 0));
        hp = z.getHealth();
        helper.assertTrue(CombatMoves.dashKey(a, ad, 0, 0), "dash while stunned");
        helper.assertTrue(!ModEffects.isStunned(a) && z.getHealth() < hp, "is a Revenge Counter");
        helper.assertTrue(ad.combat().hyperArmorUntil > helper.getLevel().getGameTime(), "with hyper armour");
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
        helper.assertTrue(CombatMoves.dashKey(a, ad, 0, 0) && !CombatMoves.isDowned(a, now) && !ad.combat().downedFlag, "roll up from the floor");

        a.teleportTo(base.x, base.y + 6, base.z);
        a.setOnGround(false);
        ad.combat().launchedAt = now;
        double stamina = ad.getStamina();
        helper.assertTrue(CombatMoves.dashKey(a, ad, 0, 0) && ad.getStamina() < stamina, "snap recovery in the air");

        a.teleportTo(base.x, base.y, base.z);
        a.setOnGround(true);
        ad.setGuarding(true);
        helper.assertTrue(CombatMoves.dashKey(a, ad, 0, 0) && ad.getDashEvadeUntil() >= now + 10, "spot dodge while guarding");
        TestPlayers.remove(helper, a);
        helper.succeed();
    }
}
