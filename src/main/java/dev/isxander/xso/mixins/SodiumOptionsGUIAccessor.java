package dev.isxander.xso.mixins;

import java.util.List;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SodiumOptionsGUI.class)
public interface SodiumOptionsGUIAccessor {
    @Accessor
    List<OptionPage> getPages();

    @Accessor
    Screen getPrevScreen();
}
