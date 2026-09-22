package dev.isxander.xso.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/** Adapts screen navigation and platform link opening across Minecraft versions. */
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
    public static void openUri(String uri) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(uri));
        *///?} else {
        net.minecraft.util.Util.getPlatform().openUri(uri);
        //?}
    }

    public static void openPath(java.nio.file.Path path) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openPath(path);
        *///?} else {
        net.minecraft.util.Util.getPlatform().openPath(path);
        //?}
    }
}
