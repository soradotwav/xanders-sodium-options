package dev.isxander.xso.mixins;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Option.class)
public interface SodiumOptionAccessor {
    @Accessor
    Identifier getId();
}
