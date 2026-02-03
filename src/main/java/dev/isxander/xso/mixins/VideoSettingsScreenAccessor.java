package dev.isxander.xso.mixins;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;

@Mixin(VideoSettingsScreen.class)
public interface VideoSettingsScreenAccessor {
    @Accessor
    Screen getPrevScreen();
}
