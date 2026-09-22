package dev.isxander.xso.mixins.yacl;

import com.google.common.collect.ImmutableList;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.OptionListWidget;
//? if >=26.1 {
/*import dev.isxander.yacl3.gui.TooltipButtonWidget;
import net.minecraft.client.input.KeyEvent;
*///?} else {
import dev.isxander.yacl3.gui.TextScaledButtonWidget;
import dev.isxander.yacl3.gui.utils.WidgetUtils;
//?}
import java.util.List;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = OptionListWidget.OptionEntry.class, remap = false)
public class OptionEntryMixin {
    @Unique
    private static final GuiEventListener xso$NON_FOCUSABLE_SPACER = new GuiEventListener() {
        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public boolean isFocused() {
            return false;
        }

        @Override
        public ComponentPath nextFocusPath(@NonNull FocusNavigationEvent event) {
            return null;
        }
    };

    @Shadow
    @Final
    public Option<?> option;

    @Shadow
    @Final
    //? if >=26.1 {
    /*private TooltipButtonWidget resetButton;
    *///?} else {
    private TextScaledButtonWidget resetButton;
    //?}

    @Shadow
    @Final
    public AbstractWidget widget;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    //? if >=26.1 {
    /*private void xso$activateResetButtonWithKeyboard(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.resetButton != null
                && this.resetButton.isFocused()
                && this.resetButton.keyPressed(event)) {
    *///?} else {
    private void xso$activateResetButtonWithKeyboard(
            int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.resetButton != null
                && this.resetButton.isFocused()
                && WidgetUtils.keyPressed(this.resetButton, keyCode, scanCode, modifiers)) {
            //?}
            ((OptionListWidget.OptionEntry) (Object) this).setFocused(this.widget);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "children", at = @At("HEAD"), cancellable = true)
    private void xso$skipSpacerFromTabFocus(CallbackInfoReturnable<List<? extends GuiEventListener>> cir) {
        if (xso$isEmptySpacerOption()) {
            cir.setReturnValue(ImmutableList.of(xso$NON_FOCUSABLE_SPACER));
        }
    }

    @Unique
    private boolean xso$isEmptySpacerOption() {
        return this.option instanceof LabelOption labelOption
                && labelOption.label().getString().isBlank();
    }
}
