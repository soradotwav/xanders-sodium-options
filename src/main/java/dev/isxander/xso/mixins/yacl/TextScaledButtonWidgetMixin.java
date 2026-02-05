package dev.isxander.xso.mixins.yacl;

import dev.isxander.yacl3.gui.TextScaledButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Temporary scaling fix for the reset button pending YACL fix merge
@Mixin(value = TextScaledButtonWidget.class, remap = false)
public abstract class TextScaledButtonWidgetMixin extends ButtonWidget.Text {

    @Shadow
    public float textScale;

    protected TextScaledButtonWidgetMixin(
            int x,
            int y,
            int width,
            int height,
            net.minecraft.text.Text text,
            PressAction pressAction,
            NarrationSupplier narrationSupplier) {
        super(x, y, width, height, text, pressAction, narrationSupplier);
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.drawButton(context);

        if (Math.abs(textScale - 1.0f) < 0.01f) {
            this.drawLabel(context.getHoverListener(this, DrawContext.HoverType.NONE));
            return;
        }

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        net.minecraft.text.Text message = getMessage();

        float centerX = getX() + getWidth() / 2.0f;
        float centerY = getY() + getHeight() / 2.0f;

        int textWidth = textRenderer.getWidth(message);
        float scaledX = centerX - (textWidth * textScale) / 2.0f;
        float scaledY = centerY - (textRenderer.fontHeight * textScale) / 2.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(scaledX, scaledY);
        context.getMatrices().scale(textScale, textScale);

        int color = 0xFFFFFFFF;
        if (!this.active) {
            color = 0xFFA0A0A0;
        }
        context.drawText(textRenderer, message, 0, 0, color, true);

        context.getMatrices().popMatrix();
    }
}
