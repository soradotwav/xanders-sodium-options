package dev.isxander.xso.mixins;

import dev.isxander.xso.utils.XsoDonationButton;
import dev.isxander.yacl3.gui.YACLScreen;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.gui.tab.TabManager;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = YACLScreen.class)
public abstract class YACLScreenMixin extends Screen {
    @Final
    @Shadow
    public TabManager tabManager;

    @Shadow
    public dev.isxander.yacl3.gui.tab.ScrollableNavigationBar tabNavigationBar;

    @Unique
    private ButtonWidget xso$donationButton;

    protected YACLScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void xso$addDonationButton(CallbackInfo ci) {
        TextFieldWidget searchField = null;
        for (var child : this.children()) {
            if (child instanceof TextFieldWidget tf) {
                searchField = tf;
                break;
            }
        }

        if (searchField != null) {
            if (this.xso$donationButton != null && this.children().contains(this.xso$donationButton)) return;

            int buttonSize = 20;
            int spacing = 4;

            int originalWidth = searchField.getWidth();
            searchField.setWidth(originalWidth - buttonSize - spacing);

            this.xso$donationButton = new XsoDonationButton(
                    searchField.getX() + searchField.getWidth() + spacing,
                    searchField.getY() + (searchField.getHeight() - buttonSize) / 2,
                    buttonSize,
                    buttonSize);
            this.addDrawableChild(this.xso$donationButton);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void xso$updateDonationButtonVisibility(CallbackInfo ci) {
        if (this.xso$donationButton == null) {
            return;
        }

        boolean isFirstTab = false;
        if (this.tabNavigationBar != null) {
            List<Tab> tabs = this.tabNavigationBar.getTabs();
            if (!tabs.isEmpty()) {
                isFirstTab = this.tabManager.getCurrentTab() == tabs.getFirst();
            }
        }
        this.xso$donationButton.visible = isFirstTab;
        this.xso$donationButton.active = isFirstTab;
    }
}
