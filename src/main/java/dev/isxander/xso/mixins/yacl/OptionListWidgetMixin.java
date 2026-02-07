package dev.isxander.xso.mixins.yacl;

import dev.isxander.xso.utils.CategoryDescriptions;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.gui.DescriptionWithName;
import dev.isxander.yacl3.gui.OptionListWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = OptionListWidget.class, remap = false)
public class OptionListWidgetMixin {

    @Shadow
    @Final
    private ConfigCategory category;

    @Unique
    private DescriptionWithName xso$categoryDefault;

    @ModifyVariable(method = "setHoverDescription", at = @At("HEAD"), argsOnly = true)
    private DescriptionWithName xso$replaceEmptyHover(DescriptionWithName incoming) {
        if (CategoryDescriptions.isLabelOptionSpacer(incoming) || CategoryDescriptions.hasEmptyDescription(incoming)) {
            if (xso$categoryDefault == null) {
                xso$categoryDefault = CategoryDescriptions.getDefault(category.name());
            }
            return xso$categoryDefault;
        }
        return incoming;
    }
}
