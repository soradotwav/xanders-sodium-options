package dev.isxander.xso.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class XsoDonationButton extends ButtonWidget {
    public static final Identifier KOFI_ICON_ID = Identifier.of("xanders-sodium-options", "kofi-icon");
    private static final int ICON_SIZE = 12;
    private static final String KO_FI_URL = "https://ko-fi.com/jellysquid_";

    public XsoDonationButton(int x, int y, int width, int height) {
        super(
                x,
                y,
                width,
                height,
                net.minecraft.text.Text.empty(),
                button -> openDonationLink(MinecraftClient.getInstance().currentScreen),
                ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        this.setTooltip(Tooltip.of(net.minecraft.text.Text.translatable("xso.donation.tooltip")));
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.drawButton(context);
        context.drawGuiTexture(
                RenderPipelines.GUI_TEXTURED,
                KOFI_ICON_ID,
                getX() + (getWidth() - ICON_SIZE) / 2,
                getY() + (getHeight() - ICON_SIZE) / 2,
                ICON_SIZE,
                ICON_SIZE);
    }

    public static void openDonationLink(Screen screen) {
        net.minecraft.text.Text title = net.minecraft.text.Text.translatable("xso.donation.confirm.title");
        net.minecraft.text.Text message = net.minecraft.text.Text.translatable("xso.donation.confirm.message");

        MinecraftClient.getInstance()
                .setScreen(new ConfirmLinkScreen(
                        confirmed -> {
                            if (confirmed) {
                                Util.getOperatingSystem().open(KO_FI_URL);
                            }
                            MinecraftClient.getInstance().setScreen(screen);
                        },
                        title,
                        message,
                        KO_FI_URL,
                        ScreenTexts.CANCEL,
                        true));
    }
}
