package dev.isxander.xso.mixins;

import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatter;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SliderControl.class)
public interface SliderControlAccessor {
    @Accessor
    int getMin();

    @Accessor
    int getMax();

    @Accessor
    int getInterval();

    @Accessor
    ControlValueFormatter getMode();
}
