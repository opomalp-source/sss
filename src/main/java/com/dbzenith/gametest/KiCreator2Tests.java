package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.skill.CustomTechniques;
import com.dbzenith.skill.CustomTechniques.Kind;
import com.dbzenith.skill.CustomTechniques.Spec;
import com.dbzenith.skill.KiTraits;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/** CX-7: Ki Creator v2 traits on real targets. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KiCreator2Tests {
    private static final String EMPTY = "empty";

    private KiCreator2Tests() {}

    private static Zombie dummy(GameTestHelper helper, BlockPos rel) {
        Zombie z = helper.spawn(EntityType.ZOMBIE, rel);
        z.setNoAi(true);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
        z.setHealth(500);
        return z;
    }

    @GameTest(template = EMPTY)
    public static void kiTypesAndHitModifiersApply(GameTestHelper helper) {
        Zombie a = dummy(helper, new BlockPos(1, 2, 1)), b = dummy(helper, new BlockPos(3, 2, 1)), c = dummy(helper, new BlockPos(1, 2, 3));
        ServerPlayer p = TestPlayers.create(helper);
        Vec3 push = new Vec3(1, 0, 0);
        KiTraits.onHit(p, p, Technique.KiType.BURNING, 0, a, 10, push, null);
        helper.assertTrue(a.isOnFire(), "burning ki sets fire");
        KiTraits.onHit(p, p, Technique.KiType.FREEZING, Technique.STUN, b, 10, push, null);
        helper.assertTrue(b.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && ModEffects.isStunned(b), "freezing slows, the stun modifier stuns");
        float hb = b.getHealth(), hc = c.getHealth();
        Set<Integer> once = new HashSet<>();
        KiTraits.onHit(p, p, Technique.KiType.PURE, Technique.CHAIN, a, 40, push, once);
        helper.assertTrue(b.getHealth() < hb && c.getHealth() < hc, "chain leaps to the others");
        hb = b.getHealth();
        KiTraits.onHit(p, p, Technique.KiType.PURE, Technique.CHAIN, a, 40, push, once);
        helper.assertTrue(b.getHealth() == hb, "but only once per attack (beams pulse)");
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setBody(d.getDerived().maxBody() * 0.5);
        double before = d.getBody();
        KiTraits.onHit(p, p, Technique.KiType.DRAINING, 0, a, 100, push, null);
        helper.assertTrue(d.getBody() > before, "draining ki heals the thrower");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void placedMinesWaitThenGoOff(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        p.teleportTo(at.x, at.y, at.z);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, at.add(4, 0, 0));
        Technique mine = CustomTechniques.build(0, new Spec("Mine", Kind.BLAST, 3, 0, 0xFFFFFF, CustomTechniques.Method.PLACED,
                CustomTechniques.Origin.HAND, Technique.KiType.PURE));
        helper.assertTrue(mine.has(Technique.PLACED), "the method makes a mine");
        TechniqueHandler.spawn(helper.getLevel(), p, mine, 60);
        Zombie z = dummy(helper, new BlockPos(5, 2, 4));
        float hp = z.getHealth();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(z.getHealth() == hp, "the mine waits while no one is near");
            var mines = helper.getLevel().getEntitiesOfClass(com.dbzenith.skill.KiBlastEntity.class, p.getBoundingBox().inflate(16));
            helper.assertTrue(mines.size() == 1, "one mine is out");
            Vec3 m = mines.get(0).position();
            z.teleportTo(m.x, m.y - 0.5, m.z);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(z.getHealth() < hp, "and goes off when someone comes close");
            TestPlayers.remove(helper, p);
        });
    }
}
