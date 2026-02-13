package dev.isxander.xso.mixins;

import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VideoSettingsScreen.class)
public interface VideoSettingsScreenAccessor {
    @Accessor
    Screen getPrevScreen();
}
