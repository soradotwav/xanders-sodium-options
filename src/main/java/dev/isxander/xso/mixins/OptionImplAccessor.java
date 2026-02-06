package dev.isxander.xso.mixins;

import net.caffeinemc.mods.sodium.client.gui.options.OptionImpl;
import net.caffeinemc.mods.sodium.client.gui.options.binding.OptionBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionImpl.class)
public interface OptionImplAccessor<S, T> {
    @Accessor
    OptionBinding<S, T> getBinding();
}
