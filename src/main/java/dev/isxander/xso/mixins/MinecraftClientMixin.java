package dev.isxander.xso.mixins;

import dev.isxander.xso.XandersSodiumOptions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen modifyScreen(Screen screen) {
        if (XandersSodiumOptions.shouldConvertGui() && screen instanceof VideoSettingsScreen videoSettingsScreen) {
            var accessor = (VideoSettingsScreenAccessor) videoSettingsScreen;
            var pages = net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.getModOptions()
                    .stream()
                    .flatMap(mod -> mod.pages().stream())
                    .filter(page -> page instanceof OptionPage)
                    .map(page -> (OptionPage) page)
                    .toList();

            return XandersSodiumOptions.wrapSodiumScreen(videoSettingsScreen, pages, accessor.getPrevScreen());
        }

        return screen;
    }
}
