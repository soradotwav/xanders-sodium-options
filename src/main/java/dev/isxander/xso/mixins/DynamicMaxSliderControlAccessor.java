package dev.isxander.xso.mixins;

import java.util.function.IntSupplier;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatter;
import net.caffeinemc.mods.sodium.client.gui.options.control.DynamicMaxSliderControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DynamicMaxSliderControl.class)
public interface DynamicMaxSliderControlAccessor {
    @Accessor
    int getMin();

    @Accessor
    IntSupplier getMax();

    @Accessor
    int getInterval();

    @Accessor
    ControlValueFormatter getMode();
}
