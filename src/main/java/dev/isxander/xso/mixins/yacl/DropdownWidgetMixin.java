package dev.isxander.xso.mixins.yacl;

import dev.isxander.xso.utils.XsoDonationScope;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerPopupWidget;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import dev.isxander.yacl3.gui.controllers.dropdown.AbstractDropdownController;
import dev.isxander.yacl3.gui.controllers.dropdown.DropdownWidget;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DropdownWidget.class, remap = false)
public abstract class DropdownWidgetMixin<T> extends ControllerPopupWidget<AbstractDropdownController<T>> {
    @Shadow
    protected Dimension<Integer> dropdownDim;

    @Shadow
    public abstract int dropdownLength();

    protected DropdownWidgetMixin(
            AbstractDropdownController<T> control, YACLScreen screen, Dimension<Integer> dim,
            ControllerWidget<?> entryWidget) {
        super(control, screen, dim, entryWidget);
    }

    //? if >=26.1 {
    /*@Inject(method = "extractRenderState", at = @At("HEAD"))
    private void xso$drawDropdownBacking(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    *///?} else {
    @Inject(method = "render", at = @At("HEAD"), remap = true)
    private void xso$drawDropdownBacking(
            GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        //?}
        if (!(this.screen instanceof XsoDonationScope scope) || !scope.xso$isDonationScoped()
                || this.dropdownLength() == 0) {
            return;
        }

        graphics.fill(
                this.dropdownDim.x(), this.dropdownDim.y(),
                this.dropdownDim.xLimit(), this.dropdownDim.yLimit(), 0xFF202020);
    }
}
