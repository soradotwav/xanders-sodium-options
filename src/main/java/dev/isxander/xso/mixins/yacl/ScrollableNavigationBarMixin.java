//? if =26.3 {
/*package dev.isxander.xso.mixins.yacl;

import dev.isxander.xso.utils.ScreenCompat;
import dev.isxander.xso.utils.XsoDonationScope;
import dev.isxander.yacl3.gui.tab.ScrollableNavigationBar;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ScrollableNavigationBar.class)
public abstract class ScrollableNavigationBarMixin {
    // SDL's Cocoa horizontal sign is opposite GLFW's.
    // Leave the vertical axis (including Shift-wheel conversion) untouched.
    @ModifyVariable(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 1)
    private double xso$restoreMacHorizontalScroll(double horizontal) {
        if (Util.getPlatform() == Util.OS.OSX
                && ScreenCompat.getScreen(Minecraft.getInstance()) instanceof XsoDonationScope scope
                && scope.xso$isDonationScoped()) {
            return -horizontal;
        }
        return horizontal;
    }
}
*///?}
