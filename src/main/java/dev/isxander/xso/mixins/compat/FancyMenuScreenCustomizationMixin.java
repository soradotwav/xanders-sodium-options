package dev.isxander.xso.mixins.compat;

import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.keksuccino.fancymenu.customization.ScreenCustomization", remap = false)
public abstract class FancyMenuScreenCustomizationMixin {
    @Inject(method = "reInitCurrentScreen(ZZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xso$skipReInitForYaclScreen(boolean resetGuiScale, boolean restoreScreen, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof YACLScreen) {
            ci.cancel();
        }
    }
}
