package com.dbzenith.client.ui;

import com.dbzenith.DBZenith;
import com.dbzenith.client.screen.SettingsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Ways into the settings: a button on the pause menu and the mod list's "Config" button. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class SettingsEntry {
    private SettingsEntry() {}

    /** Called from client setup: the Mods screen's Config button opens our settings. */
    public static void registerConfigScreen() {
        ModList.get().getModContainerById(DBZenith.MOD_ID).ifPresent(c -> c.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent))));
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause)) return;
        event.addListener(ThemedButton.of(Component.translatable("settings.dbzenith.pause_button"),
                b -> event.getScreen().getMinecraft().setScreen(new SettingsScreen(pause))).bounds(6, 6, 104, 18).build());
    }
}
