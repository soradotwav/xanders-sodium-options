package dev.isxander.xso.compat;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.impl.controller.DropdownStringControllerBuilderImpl;
import java.io.IOException;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class IrisCompat {

    public static ConfigCategory createShaderPacksCategory() {
        var shaderPackList = Option.<String>createBuilder()
                .name(Text.translatable("options.iris.selectedShaderPack"))
                .description(OptionDescription.of(Text.translatable("options.iris.selectedShaderPack.description")))
                .binding(
                        Iris.getIrisConfig().getShaderPackName().orElse(""),
                        () -> Iris.getIrisConfig().getShaderPackName().orElse(""),
                        (val) -> {
                            if (val.isEmpty()) val = null;
                            Iris.getIrisConfig().setShaderPackName(val);
                            try {
                                Iris.getIrisConfig().save();
                                Iris.reload();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .controller((opt) -> {
                    try {
                        return new DropdownStringControllerBuilderImpl(opt)
                                .allowAnyValue(false)
                                .values(Iris.getShaderpacksDirectoryManager().enumerate());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .available(Iris.getIrisConfig().areShadersEnabled())
                .build();

        var enableShaders = Option.<Boolean>createBuilder()
                .name(Text.translatable("options.iris.enableShaders"))
                .description(OptionDescription.of(Text.translatable("options.iris.enableShaders.description")))
                .binding(true, () -> Iris.getIrisConfig().areShadersEnabled(), (val) -> {
                    Iris.getIrisConfig().setShadersEnabled(val);
                    try {
                        Iris.getIrisConfig().save();
                        Iris.reload();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .addListener((option, event) -> {
                    if (event == OptionEventListener.Event.STATE_CHANGE) {
                        shaderPackList.setAvailable(option.pendingValue());
                    }
                })
                .controller((opt) ->
                        BooleanControllerBuilder.create(opt).coloured(true).trueFalseFormatter())
                .build();

        return ConfigCategory.createBuilder()
                .name(Text.translatable("options.iris.shaderPackSelection.title"))
                .option(LabelOption.create(Text.empty()))
                .option(ButtonOption.createBuilder()
                        .name(Text.translatable("options.iris.openShaderPackScreen"))
                        .text(Text.literal("➔"))
                        .description(OptionDescription.of(
                                Text.translatable("options.iris.openShaderPackScreen.description")))
                        .action((screen, opt) -> MinecraftClient.getInstance().setScreen(new ShaderPackScreen(screen)))
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Text.translatable("options.iris.downloadShaders"))
                        .text(Text.literal("➔"))
                        .description(
                                OptionDescription.of(Text.translatable("options.iris.downloadShaders.description")))
                        .action((screen, opt) -> MinecraftClient.getInstance()
                                .setScreen(new ConfirmLinkScreen(
                                        (bl) -> {
                                            if (bl) {
                                                Util.getOperatingSystem().open("https://modrinth.com/shaders");
                                            }
                                            MinecraftClient.getInstance().setScreen(screen);
                                        },
                                        "https://modrinth.com/shaders",
                                        true)))
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Text.translatable("options.iris.openShaderPackFolder"))
                        .text(Text.literal("➔"))
                        .description(OptionDescription.of(
                                Text.translatable("options.iris.openShaderPacksFolder.description")))
                        .action((screen, opt) -> Util.getOperatingSystem()
                                .open(FabricLoader.getInstance().getGameDir().resolve("shaderpacks/")))
                        .build())
                .option(LabelOption.create(Text.translatable("options.iris.shaderPackOptions")))
                .option(enableShaders)
                .option(shaderPackList)
                .build();
    }

    public static boolean isIrisSettingsPage(Text pageName) {
        return pageName.getString().equals("Settings");
    }
}
