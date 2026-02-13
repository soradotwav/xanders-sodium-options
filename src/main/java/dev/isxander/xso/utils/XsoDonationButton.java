package dev.isxander.xso.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

public class XsoDonationButton extends Button {
    public static final Identifier KOFI_ICON_ID =
            Identifier.fromNamespaceAndPath("xanders-sodium-options", "kofi-icon");
    private static final int ICON_SIZE = 12;
    private static final String KO_FI_URL = "https://ko-fi.com/jellysquid_";

    public XsoDonationButton(int x, int y, int width, int height) {
        super(
                x,
                y,
                width,
                height,
                net.minecraft.network.chat.Component.empty(),
                button -> openDonationLink(Minecraft.getInstance().screen),
                Button.DEFAULT_NARRATION);
        this.setTooltip(Tooltip.create(net.minecraft.network.chat.Component.translatable("xso.donation.tooltip")));
    }

    @Override
    protected void renderContents(@NotNull GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        this.renderDefaultSprite(context);
        context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                KOFI_ICON_ID,
                getX() + (getWidth() - ICON_SIZE) / 2,
                getY() + (getHeight() - ICON_SIZE) / 2,
                ICON_SIZE,
                ICON_SIZE);
    }

    public static void openDonationLink(Screen screen) {
        net.minecraft.network.chat.Component title =
                net.minecraft.network.chat.Component.translatable("xso.donation.confirm.title");
        net.minecraft.network.chat.Component message =
                net.minecraft.network.chat.Component.translatable("xso.donation.confirm.message");

        Minecraft.getInstance()
                .setScreen(new ConfirmLinkScreen(
                        confirmed -> {
                            if (confirmed) {
                                Util.getPlatform().openUri(KO_FI_URL);
                            }
                            Minecraft.getInstance().setScreen(screen);
                        },
                        title,
                        message,
                        KO_FI_URL,
                        CommonComponents.GUI_CANCEL,
                        true));
    }
}
