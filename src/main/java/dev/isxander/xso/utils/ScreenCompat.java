package dev.isxander.xso.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/** Screen ownership moved from Minecraft to Gui in 26.2. */
public final class ScreenCompat {
    private ScreenCompat() {
    }

    public static @Nullable Screen getScreen(Minecraft client) {
        //? if >=26.2 {
        /*return client.gui.screen();
        *///?} else {
        return client.screen;
        //?}
    }

    public static void setScreen(Minecraft client, @Nullable Screen screen) {
        //? if >=26.2 {
        /*client.gui.setScreen(screen);
        *///?} else {
        client.setScreen(screen);
        //?}
    }
}
