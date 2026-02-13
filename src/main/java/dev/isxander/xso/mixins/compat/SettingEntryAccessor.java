package dev.isxander.xso.mixins.compat;

import dev.lambdaurora.lambdynlights.config.SettingEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SettingEntry.class)
public interface SettingEntryAccessor<T> {
    @Accessor("defaultValue")
    T getDefaultValue();
}
