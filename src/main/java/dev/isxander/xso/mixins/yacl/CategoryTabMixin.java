package dev.isxander.xso.mixins.yacl;

import dev.isxander.xso.utils.CategoryDescriptions;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.gui.OptionDescriptionWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = YACLScreen.CategoryTab.class, remap = false)
public class CategoryTabMixin {

    @Shadow
    private OptionDescriptionWidget descriptionWidget;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void xso$setDefaultDescription(
            YACLScreen screen, ConfigCategory category, ScreenRectangle tabArea, CallbackInfo ci) {
        this.descriptionWidget.setOptionDescription(CategoryDescriptions.getDefault(category.name()));
    }
}
