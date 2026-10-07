package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-17a: the Spirit Bomb gathers from those who lend it energy, spares them, and is thrown when cast again. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritBombTests {
    private static final String EMPTY = "empty";

    private SpiritBombTests() {}

    private static KiBlastEntity bombOf(GameTestHelper helper, ServerPlayer p) {
        return helper.getLevel().getEntitiesOfClass(KiBlastEntity.class, p.getBoundingBox().inflate(40),
                b -> b.isSpiritBomb() && b.getOwner() == p).stream().findFirst().orElse(null);
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void theSpiritBombGathersFromHelpersAndSparesThem(GameTestHelper helper) {
        ServerPlayer caster = TestPlayers.create(helper), friend = TestPlayers.create(helper);
        friend.teleportTo(caster.getX() + 3, caster.getY(), caster.getZ());
        PlayerData cd = ModCapabilities.getOrThrow(caster), fd = ModCapabilities.getOrThrow(friend);
        cd.setKi(cd.getDerived().maxKi());
        fd.setKi(fd.getDerived().maxKi());
        caster.setGameMode(net.minecraft.world.level.GameType.CREATIVE);                // a fresh character could not pay for it
        var raised = TechniqueHandler.use(caster, Techniques.SPIRIT_BOMB, true);
        helper.assertTrue(raised == TechniqueHandler.Result.FIRED, "raised: " + raised);
        KiBlastEntity bomb = bombOf(helper, caster);
        helper.assertTrue(bomb != null && bomb.isHovering(), "it hovers overhead, gathering");
        float size0 = bomb.getSize();
        fd.setCharging(true);                                                            // the friend lends their energy
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(bomb.gathered() > 0 && bomb.getSize() > size0, "it grows: " + bomb.gathered() + " ki, size " + bomb.getSize());
            helper.assertTrue(bomb.spares(friend) && bomb.spares(caster), "the giver and the thrower are spared");
            helper.assertTrue(TechniqueHandler.use(caster, Techniques.SPIRIT_BOMB, true) == TechniqueHandler.Result.FIRED, "cast again");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!bomb.isHovering(), "thrown");
                fd.setCharging(false);
                bomb.discard();
                TestPlayers.remove(helper, caster);
                TestPlayers.remove(helper, friend);
                helper.succeed();
            });
        });
    }
}
