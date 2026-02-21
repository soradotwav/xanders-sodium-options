package dev.isxander.xso.mixins.yacl;

import dev.isxander.xso.utils.CategoryDescriptions;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.gui.DescriptionWithName;
import dev.isxander.yacl3.gui.OptionListWidget;
import java.util.function.Predicate;
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

    @ModifyVariable(
            method =
                    "nextEntry(Lnet/minecraft/client/gui/navigation/ScreenDirection;Ljava/util/function/Predicate;Ldev/isxander/yacl3/gui/OptionListWidget$Entry;)Ldev/isxander/yacl3/gui/OptionListWidget$Entry;",
            at = @At("HEAD"),
            argsOnly = true)
    private Predicate<OptionListWidget.Entry> xso$skipEmptySpacers(Predicate<OptionListWidget.Entry> predicate) {
        return entry -> predicate.test(entry) && !xso$isEmptySpacer(entry);
    }

    @Unique
    private static boolean xso$isEmptySpacer(OptionListWidget.Entry entry) {
        if (entry instanceof OptionListWidget.OptionEntry optionEntry) {
            Option<?> option = optionEntry.option;
            if (option instanceof LabelOption labelOption) {
                return labelOption.label().getString().isBlank();
            }
        }
        return false;
    }
}
