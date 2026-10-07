package com.dbzenith.client;

import com.dbzenith.DBZenith;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Client-only listeners on the Forge event bus. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    /**
     * Dev automation, inert unless the JVM flags are set (they only are in the dev run configs):
     * {@code -Ddbzenith.devScreenshots=100,200} saves screenshots that many ticks after joining a world;
     * {@code -Ddbzenith.devQuitAfter=300} closes the client after that many in-world ticks.
     */
    private static final Set<Integer> DEV_SCREENSHOT_TICKS = parseTicks(System.getProperty("dbzenith.devScreenshots", ""));
    private static final int DEV_QUIT_AFTER = Integer.getInteger("dbzenith.devQuitAfter", -1);
    private static final boolean DEV_AUTOMATION = Boolean.getBoolean("dbzenith.devAutomation");
    private static int ticksInWorld;
    private static String pendingShot;
    private static int pendingShotTicks;

    private ClientEvents() {}

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPlayerData.clear();
        ClientCombatState.clear();
        ClientStruggle.clear();
        ClientPublicStates.clear();
        ClientRadar.clear();
        ClientHooks.resetCreationPrompt();
        ticksInWorld = 0;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        devAutoReconnect(mc);
        if (mc.level == null || mc.player == null) return;
        ClientHooks.maybeOpenCreation();
        ticksInWorld++;
        if (pendingShot != null && --pendingShotTicks <= 0) {
            String shot = pendingShot;
            pendingShot = null;
            devScreenshot(shot, 0);
            if (mc.screen instanceof com.dbzenith.client.screen.StatScreen || mc.screen instanceof com.dbzenith.client.screen.FormScreen
                    || mc.screen instanceof com.dbzenith.client.screen.CharacterCreationScreen
                    || mc.screen instanceof com.dbzenith.client.screen.DeckScreen || mc.screen instanceof com.dbzenith.client.screen.QuestScreen
                    || mc.screen instanceof com.dbzenith.client.screen.PlanetScreen || mc.screen instanceof com.dbzenith.client.screen.LifeScreen || mc.screen instanceof com.dbzenith.client.screen.RacialScreen || mc.screen instanceof com.dbzenith.client.screen.FaceScreen) mc.setScreen(null);
            if (shot.startsWith("third_") || shot.startsWith("front_")) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }
        if (DEV_SCREENSHOT_TICKS.contains(ticksInWorld)) {
            Screenshot.grab(mc.gameDirectory, "dbz_dev_" + ticksInWorld + ".png", mc.getMainRenderTarget(),
                    msg -> DBZenith.LOGGER.info("[dev] {}", msg.getString()));
        }
        if (DEV_QUIT_AFTER > 0 && ticksInWorld == DEV_QUIT_AFTER) {
            DBZenith.LOGGER.info("[dev] devQuitAfter reached, stopping client");
            mc.stop();
        }
    }

    /** Dev automation: save a named screenshot now (requested by /dbz devshot). */
    public static void devScreenshot(String name, int delayTicks) {
        if (!DEV_AUTOMATION) return;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.CameraType wanted = name.startsWith("third_") ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                : name.startsWith("front_") ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                : name.startsWith("first_") ? net.minecraft.client.CameraType.FIRST_PERSON : null;
        if (wanted != null && mc.options.getCameraType() != wanted) {
            mc.options.setCameraType(wanted);
            delayTicks = Math.max(delayTicks, 4);
        }
        if (name.startsWith("radial_") && !(mc.screen instanceof com.dbzenith.client.ui.RadialMenuScreen)) {
            mc.setScreen(com.dbzenith.client.ui.RadialMenuScreen.dev(Integer.parseInt(name.substring(7).replaceAll("\\D.*", ""))));
            delayTicks = Math.max(delayTicks, 6);
        }
        if (name.startsWith("barber_") && mc.player != null && !(mc.screen instanceof com.dbzenith.client.screen.HairEditorScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.HairEditorScreen(null, ClientPlayerData.get().getHairCode(), ClientPlayerData.get().getHairColor(), (c, col) -> {}));
            delayTicks = Math.max(delayTicks, 6);
        }
        if (name.startsWith("kicreator_") && !(mc.screen instanceof com.dbzenith.client.screen.KiCreatorScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.KiCreatorScreen(null));
            delayTicks = Math.max(delayTicks, 6);
        }
        if (name.startsWith("settings_") && !(mc.screen instanceof com.dbzenith.client.screen.SettingsScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.SettingsScreen(null));
            delayTicks = Math.max(delayTicks, 6);
        }
        if (name.startsWith("pathchoice_") && !(mc.screen instanceof com.dbzenith.client.screen.PathChoiceScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.PathChoiceScreen(null));
            delayTicks = Math.max(delayTicks, 6);
        }
        com.dbzenith.client.fx.CameraFx.devZoom = name.contains("zoomface") ? 14f : name.contains("zoom") ? 42f : 0f;
        com.dbzenith.client.fx.CameraFx.devTurn = name.contains("side") ? 90f : name.contains("turn") ? 45f : 0f;
        com.dbzenith.client.anim.AnimController.devFreeze = false;
        com.dbzenith.client.fx.VfxV3.devSpeedLines = name.contains("speedlines");
        com.dbzenith.client.motion.MotionDev.fromShot(name, delayTicks);
        mc.options.hideGui = name.contains("hidegui");
        if (name.contains("labels")) com.dbzenith.config.DBZConfig.CLIENT.animationLabels.set(true);
        if (name.contains("respawn") && mc.player != null && mc.player.isDeadOrDying()) mc.player.respawn();
        if (name.startsWith("judgement_") && !(mc.screen instanceof com.dbzenith.client.screen.JudgementScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.JudgementScreen(true, 97));
            delayTicks = Math.max(delayTicks, 6);
        }
        if (name.contains("hudzenith")) com.dbzenith.config.DBZConfig.CLIENT.hudStyle.set(0);
        if (name.contains("hudclassic")) com.dbzenith.config.DBZConfig.CLIENT.hudStyle.set(1);
        if (name.contains("hudminimal")) com.dbzenith.config.DBZConfig.CLIENT.hudStyle.set(2);
        if (name.contains("hudornate")) com.dbzenith.config.DBZConfig.CLIENT.hudStyle.set(3);
        if (name.contains("hudclean")) com.dbzenith.config.DBZConfig.CLIENT.hudStyle.set(4);
        if (name.contains("uiclassic")) com.dbzenith.config.DBZConfig.CLIENT.uiStyle.set(1);
        if (name.contains("uizenith")) com.dbzenith.config.DBZConfig.CLIENT.uiStyle.set(0);
        if (name.contains("hdoff") || name.contains("artclassic")) com.dbzenith.config.DBZConfig.CLIENT.artStyle.set(2);
        if (name.contains("hdon") || name.contains("arthd")) com.dbzenith.config.DBZConfig.CLIENT.artStyle.set(1);
        if (name.contains("artpainted")) com.dbzenith.config.DBZConfig.CLIENT.artStyle.set(0);
        if (name.startsWith("face_") && !(mc.screen instanceof com.dbzenith.client.screen.FaceScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.FaceScreen(null));
            delayTicks = Math.max(delayTicks, 6);
        }
        if (name.startsWith("universal_") && !(mc.screen instanceof com.dbzenith.client.screen.RacialScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.RacialScreen(null, true));
            delayTicks = Math.max(delayTicks, 6);
        }
        if (name.startsWith("racial_") && !(mc.screen instanceof com.dbzenith.client.screen.RacialScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.RacialScreen(null));
            delayTicks = Math.max(delayTicks, 6);
        }
        if (name.contains("cutin_")) com.dbzenith.client.ui.CutInOverlay.play(net.minecraft.network.chat.Component.translatable("form.dbzenith.super_saiyan"), 0xFFD040);
        int impact = name.indexOf("impact_");
        if (impact >= 0 && mc.player != null) {
            String kind = name.substring(impact + 7).replaceAll("_\\d+$", "");
            int k = java.util.List.of("PUNCH", "HEAVY", "SPIKE", "GUARD", "KI_HIT", "EXPLOSION", "PARRY", "GUARD_BREAK", "DEFLECT").indexOf(kind);
            net.minecraft.world.phys.Vec3 at = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2.5));
            if (k >= 0) com.dbzenith.client.fx.ImpactFx.onImpact(com.dbzenith.network.ImpactPacket.at(at, mc.player.getLookAngle(), k,
                    k == 5 ? 3f : 1f, 0xFFC040, -1));
        }
        if (name.contains("crater_") && mc.player != null && mc.level != null) {
            net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle().multiply(1, 0, 1).normalize();
            com.dbzenith.client.fx.ImpactFx.crater(mc.level, mc.player.position().add(look.scale(3)), 2.5f, 30);
        }
        int anim = name.indexOf("anim_");
        if (anim >= 0) com.dbzenith.client.anim.AnimController.devPreview(name.substring(anim + 5).replaceAll("_\\d+$", ""), name.contains("duet"));
        else com.dbzenith.client.DevDuet.clear();
        com.dbzenith.client.ClientFusion.devCamera(name.contains("dancecam"));
        if (name.contains("fused") && mc.player != null && ClientPublicStates.get(mc.player.getId()) != null) {   // the fused looks (12c)
            var st = ClientPublicStates.get(mc.player.getId());
            int bit = name.contains("fusedpotara") ? com.dbzenith.network.PublicStatePacket.FUSED_POTARA : name.contains("fusedfat") ? com.dbzenith.network.PublicStatePacket.FUSED_FAT
                    : name.contains("fusedthin") ? com.dbzenith.network.PublicStatePacket.FUSED_THIN : com.dbzenith.network.PublicStatePacket.FUSED_DANCE;
            ClientPublicStates.put(st.withFlags((st.flags() & ~com.dbzenith.network.PublicStatePacket.FUSED) | bit, "Devtner"));
            mc.player.refreshDisplayName();
        }
        if (name.startsWith("noscreen_") && mc.screen != null) {
            mc.setScreen(null);
            delayTicks = Math.max(delayTicks, 3);
        }
        if (name.startsWith("quests_") && !(mc.screen instanceof com.dbzenith.client.screen.QuestScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.QuestScreen(name.contains("patrol") ? com.dbzenith.quest.Quest.Giver.PATROL : com.dbzenith.quest.Quest.Giver.MASTER));
            delayTicks = Math.max(delayTicks, 5);
        }
        if (name.startsWith("racelook_") && !(mc.screen instanceof com.dbzenith.client.screen.RaceLookScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.RaceLookScreen(null));
            delayTicks = Math.max(delayTicks, 5);
        }
        if (name.startsWith("life_") && !(mc.screen instanceof com.dbzenith.client.screen.LifeScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.LifeScreen(null));
            delayTicks = Math.max(delayTicks, 5);
        }
        if (name.startsWith("planets_") && !(mc.screen instanceof com.dbzenith.client.screen.PlanetScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.PlanetScreen());
            delayTicks = Math.max(delayTicks, 5);
        }
        if (name.startsWith("create_") && !(mc.screen instanceof com.dbzenith.client.screen.CharacterCreationScreen)) {
            var screen = new com.dbzenith.client.screen.CharacterCreationScreen();
            mc.setScreen(screen.tab(name));
            delayTicks = Math.max(delayTicks, 6);
        }
        if (name.startsWith("deck_") && !(mc.screen instanceof com.dbzenith.client.screen.DeckScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.DeckScreen(null));
            delayTicks = Math.max(delayTicks, 5);
        }
        if (name.startsWith("stats_") && !(mc.screen instanceof com.dbzenith.client.screen.StatScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.StatScreen());
            delayTicks = Math.max(delayTicks, 5);
        }
        if (name.startsWith("forms_") && !(mc.screen instanceof com.dbzenith.client.screen.FormScreen)) {
            mc.setScreen(new com.dbzenith.client.screen.FormScreen(null));
            delayTicks = Math.max(delayTicks, 5);
        }
        if (delayTicks > 0) {
            pendingShot = name;
            pendingShotTicks = delayTicks;
            return;
        }
        String safe = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        Screenshot.grab(mc.gameDirectory, "dbz_" + safe + ".png", mc.getMainRenderTarget(),
                msg -> DBZenith.LOGGER.info("[dev] {}", msg.getString()));
    }

    private static int disconnectedTicks;

    /** Dev automation: the scripted test client retries a failed connection to the dev server. */
    private static void devAutoReconnect(Minecraft mc) {
        if (!DEV_AUTOMATION || !(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)) {
            disconnectedTicks = 0;
            return;
        }
        if (++disconnectedTicks == 40) {
            String address = System.getProperty("dbzenith.devServer", "localhost:25565");
            DBZenith.LOGGER.info("[dev] connection failed, retrying {}", address);
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                    new net.minecraft.client.multiplayer.ServerData("dev", address, false), false);
        }
    }

    private static Set<Integer> parseTicks(String csv) {
        if (csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Integer::parseInt).collect(Collectors.toUnmodifiableSet());
    }
}
