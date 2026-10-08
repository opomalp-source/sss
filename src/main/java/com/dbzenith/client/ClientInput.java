package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.screen.StatScreen;
import com.dbzenith.network.DashPacket;
import com.dbzenith.network.InputPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.UseTechniquePacket;
import com.dbzenith.skill.Technique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Turns key state into packets. Held keys send start/stop edges only. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientInput {
    private static boolean wasCharging;
    private static boolean wasGuarding;
    private static boolean wasHeavy;
    private static boolean wasBlast;
    private static long blastDownAt;
    /** The technique being charged (held), and whether the transform key is held (CX-23). */
    private static Technique chargingTechnique;
    private static boolean transformHeld;

    private ClientInput() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            wasCharging = wasGuarding = wasHeavy = wasBlast = false;
            return;
        }

        boolean charging = ModKeys.CHARGE.isDown() && mc.screen == null;
        if (charging != wasCharging) {
            ModNetwork.sendToServer(new InputPacket(charging ? InputPacket.Action.CHARGE_START : InputPacket.Action.CHARGE_STOP));
            wasCharging = charging;
        }
        boolean guarding = ModKeys.GUARD.isDown() && mc.screen == null;
        if (guarding != wasGuarding) {
            ModNetwork.sendToServer(new InputPacket(guarding ? InputPacket.Action.GUARD_START : InputPacket.Action.GUARD_STOP));
            wasGuarding = guarding;
        }

        boolean blast = ModKeys.KI_BLAST.isDown() && mc.screen == null;      // ki blasts (CX-19): the server times the hold
        boolean tapped = false;
        while (ModKeys.KI_BLAST.consumeClick()) tapped = true;
        if (!wasBlast && (blast || tapped)) {
            ModNetwork.sendToServer(new com.dbzenith.network.KiBlastPacket(true));
            wasBlast = true;
            blastDownAt = mc.level.getGameTime();
        }
        if (wasBlast && !blast) {
            ModNetwork.sendToServer(new com.dbzenith.network.KiBlastPacket(false));
            wasBlast = false;
            Prediction.kiBlast((int) (mc.level.getGameTime() - blastDownAt));      // a tap throws at once (phase 7)
        }

        while (ModKeys.HEAVY.consumeClick()) {                         // a heavy: the finisher of a combo (CX-19)
            byte push = com.dbzenith.network.MeleeInputPacket.push(mc.player.input.forwardImpulse, mc.player.input.leftImpulse);
            Prediction.melee(true, push);                                // shown at once (phase 7)
            ModNetwork.sendToServer(new com.dbzenith.network.MeleeInputPacket(true, push));
        }
        while (ModKeys.DASH.consumeClick()) {
            float fw = mc.player.input.forwardImpulse, st = mc.player.input.leftImpulse;
            boolean predicted = Prediction.dash(fw, st);                 // a plain dash moves you at once (phase 7)
            ModNetwork.sendToServer(new DashPacket(fw, st, predicted));
        }

        while (ModKeys.FLY.consumeClick()) ModNetwork.sendToServer(new InputPacket(InputPacket.Action.TOGGLE_FLIGHT));
        while (ModKeys.LOWER_RELEASE.consumeClick()) ModNetwork.sendToServer(new InputPacket(InputPacket.Action.LOWER_RELEASE));
        while (ModKeys.NEXT_TECHNIQUE.consumeClick()) {
            Technique t = ClientCombatState.cycle();
            if (t != null) {
                mc.player.displayClientMessage(Component.translatable("message.dbzenith.selected_technique",
                        t.name()), true);
            }
        }
        while (ModKeys.KI_ATTACK.consumeClick()) {
            if (ClientStruggle.active()) {                       // locked in a beam struggle: every press pushes
                ModNetwork.sendToServer(new com.dbzenith.network.BeamMashPacket());
                ClientStruggle.mashed();
                continue;
            }
            if (chargingTechnique != null) continue;             // already holding one (CX-23)
            Technique t = ClientCombatState.selected();
            long now = mc.level.getGameTime();
            double meter = ClientCombatState.meterCost(t);
            if (t != null && meter > 0 && !mc.player.getAbilities().instabuild && ClientPlayerData.get().getSpecial() + 1e-6 < meter) {
                mc.player.displayClientMessage(Component.translatable("message.dbzenith.no_meter",
                        (int) Math.ceil(meter / com.dbzenith.combat.engine.SpecialMeter.BAR)).withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                continue;                                                  // a super needs its bars (CX-19)
            }
            if (t != null && !ClientCombatState.onCooldown(t, now)) {   // held: it charges until let go (CX-23)
                ModNetwork.sendToServer(new com.dbzenith.network.KiChargePackets.Input(true, t.id()));
                chargingTechnique = t;
            }
        }
        if (chargingTechnique != null && !ModKeys.KI_ATTACK.isDown()) {     // let go: it fires
            ModNetwork.sendToServer(new com.dbzenith.network.KiChargePackets.Input(false, ""));
            ClientCombatState.startCooldown(chargingTechnique, mc.level.getGameTime());
            chargingTechnique = null;
        }
        while (ModKeys.TRANSFORM.consumeClick()) {
            boolean down = Screen.hasShiftDown();
            ModNetwork.sendToServer(new InputPacket(down ? InputPacket.Action.TRANSFORM_DOWN : InputPacket.Action.TRANSFORM_UP));
            transformHeld = !down;                                     // held to power up; let go and it falls back (CX-23)
        }
        if (transformHeld && !ModKeys.TRANSFORM.isDown()) {
            ModNetwork.sendToServer(new InputPacket(InputPacket.Action.TRANSFORM_RELEASE));
            transformHeld = false;
        }
        while (ModKeys.STATS.consumeClick()) mc.setScreen(new StatScreen());
        while (ModKeys.RADIAL.consumeClick()) com.dbzenith.client.ui.RadialMenuScreen.open();
        while (ModKeys.PVP.consumeClick()) ModNetwork.sendToServer(new com.dbzenith.network.PvpTogglePacket());   // PvP mode (CX-19)
        while (ModKeys.LOCK_ON.consumeClick()) LockOn.keyPressed(Screen.hasShiftDown());                          // lock-on (CX-19)
        while (ModKeys.KAIOKEN.consumeClick()) {
            ModNetwork.sendToServer(new InputPacket(Screen.hasShiftDown() ? InputPacket.Action.KAIOKEN_OFF : InputPacket.Action.KAIOKEN_UP));
        }
        while (ModKeys.SKILL.consumeClick()) {                          // shift: browse the universal skills
            com.dbzenith.data.PlayerData pd = ClientPlayerData.get();
            if (Screen.hasShiftDown()) mc.setScreen(new com.dbzenith.client.screen.RacialScreen(null, true));
            else if (pd.getSkillSelected().equals("instant_transmission") && pd.getSkillLevel("instant_transmission") > 0) {
                if (mc.level.getGameTime() >= pd.getRacialCooldown("instant_transmission")) ModNetwork.sendToServer(new com.dbzenith.network.RacialPackets.TransmitRequest());
                else ModNetwork.sendToServer(new InputPacket(InputPacket.Action.SKILL_USE));   // the server says how long
            } else ModNetwork.sendToServer(new InputPacket(InputPacket.Action.SKILL_USE));
        }
        while (ModKeys.RACIAL.consumeClick()) {                         // shift: browse the racial skills
            if (Screen.hasShiftDown()) mc.setScreen(new com.dbzenith.client.screen.RacialScreen(null));
            else ModNetwork.sendToServer(new InputPacket(InputPacket.Action.RACIAL_USE));
        }
    }
    /**
     * A bare-handed attack becomes a light blow of the combat engine (CX-19) instead of vanilla's punch: no swing, the
     * press goes to the server with the way you push. Mining with your fists still works on blocks, unless a foe is
     * close in front of you.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onAttackKey(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !mc.player.getMainHandItem().isEmpty() || mc.player.isSpectator()) return;
        if (!ClientPvp.on()) return;                                     // PvP off: a plain vanilla punch (CX-20)
        if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && !foeAhead(mc)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        byte push = com.dbzenith.network.MeleeInputPacket.push(mc.player.input.forwardImpulse, mc.player.input.leftImpulse);
        Prediction.melee(false, push);                                    // shown at once (phase 7)
        ModNetwork.sendToServer(new com.dbzenith.network.MeleeInputPacket(false, push));
    }

    /** A living thing within five blocks in front of you. */
    private static boolean foeAhead(Minecraft mc) {
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        for (net.minecraft.world.entity.LivingEntity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                mc.player.getBoundingBox().inflate(5), e -> e != mc.player && e.isAlive())) {
            net.minecraft.world.phys.Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.length() < 5 && to.normalize().dot(look) > 0.6) return true;
        }
        return false;
    }

}
