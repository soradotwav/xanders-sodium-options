package dev.isxander.xso.mixins.yacl;

import com.google.common.collect.ImmutableList;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.OptionListWidget;
import dev.isxander.yacl3.gui.TextScaledButtonWidget;
import dev.isxander.yacl3.gui.utils.WidgetUtils;
import java.util.List;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = OptionListWidget.OptionEntry.class, remap = false)
public class OptionEntryMixin {
    @Shadow
    @Final
    public Option<?> option;

    @Shadow
    @Final
    private TextScaledButtonWidget resetButton;

    @Shadow
    @Final
    public AbstractWidget widget;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void xso$activateResetButtonWithKeyboard(
            int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.resetButton != null
                && this.resetButton.isFocused()
                && WidgetUtils.keyPressed(this.resetButton, keyCode, scanCode, modifiers)) {
            ((OptionListWidget.OptionEntry) (Object) this).setFocused(this.widget);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "children", at = @At("HEAD"), cancellable = true)
    private void xso$skipSpacerFromTabFocus(CallbackInfoReturnable<List<? extends GuiEventListener>> cir) {
        if (xso$isEmptySpacerOption()) {
            cir.setReturnValue(ImmutableList.of());
        }
    }

    @Unique
    private boolean xso$isEmptySpacerOption() {
        return this.option instanceof LabelOption labelOption
                && labelOption.label().getString().isBlank();
    }
}
