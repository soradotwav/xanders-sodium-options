package dev.isxander.xso.mixins;

import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TabManager.class)
public class TabManagerMixin {

    @Inject(method = "setCurrentTab", at = @At("RETURN"))
    private void onTabChanged(Tab tab, boolean clickSound, CallbackInfo ci) {
        if (tab instanceof YACLScreen.CategoryTab categoryTab) {
            categoryTab.updateButtons();
        }
    }
}
