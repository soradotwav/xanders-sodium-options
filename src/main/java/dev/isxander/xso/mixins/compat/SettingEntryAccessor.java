package dev.isxander.xso.mixins.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.config.SettingEntry", remap = false)
public interface SettingEntryAccessor<T> {
    @Accessor("defaultValue")
    T getDefaultValue();
}
