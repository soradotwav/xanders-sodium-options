package dev.isxander.xso.mixins;

import dev.isxander.xso.XandersSodiumOptions;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen modifyScreen(Screen screen) {
        if (XandersSodiumOptions.shouldConvertGui() && screen instanceof VideoSettingsScreen videoSettingsScreen) {
            var accessor = (VideoSettingsScreenAccessor) videoSettingsScreen;
            var modOptions = net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.getModOptions();

            return XandersSodiumOptions.wrapSodiumScreen(videoSettingsScreen, modOptions, accessor.getPrevScreen());
        }

        return screen;
    }
}
