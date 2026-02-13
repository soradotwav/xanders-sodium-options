package dev.isxander.xso.mixins.yacl;

import dev.isxander.yacl3.gui.TextScaledButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Temporary scaling fix for the reset button pending YACL fix merge
@Mixin(value = TextScaledButtonWidget.class, remap = false)
public abstract class TextScaledButtonWidgetMixin extends Button.Plain {

    @Shadow
    public float textScale;

    protected TextScaledButtonWidgetMixin(
            int x,
            int y,
            int width,
            int height,
            net.minecraft.network.chat.Component text,
            OnPress pressAction,
            CreateNarration narrationSupplier) {
        super(x, y, width, height, text, pressAction, narrationSupplier);
    }

    @Override
    protected void renderContents(@NotNull GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        this.renderDefaultSprite(context);

        if (Math.abs(textScale - 1.0f) < 0.01f) {
            this.renderDefaultLabel(context.textRendererForWidget(this, GuiGraphics.HoveredTextEffects.NONE));
            return;
        }

        Font textRenderer = Minecraft.getInstance().font;
        net.minecraft.network.chat.Component message = getMessage();

        float centerX = getX() + getWidth() / 2.0f;
        float centerY = getY() + getHeight() / 2.0f;

        int textWidth = textRenderer.width(message);
        float scaledX = centerX - (textWidth * textScale) / 2.0f;
        float scaledY = centerY - (textRenderer.lineHeight * textScale) / 2.0f;

        context.pose().pushMatrix();
        context.pose().translate(scaledX, scaledY);
        context.pose().scale(textScale, textScale);

        int color = 0xFFFFFFFF;
        if (!this.active) {
            color = 0xFFA0A0A0;
        }
        context.drawString(textRenderer, message, 0, 0, color, true);

        context.pose().popMatrix();
    }
}
