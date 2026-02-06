package dev.isxander.xso.compat;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.impl.controller.DropdownStringControllerBuilderImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.parsing.IrisOptions;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.util.Util;

import java.io.IOException;
import java.util.Optional;

public class IrisCompat {
    public static Optional<ConfigCategory> replaceShaderPackPage(SodiumOptionsGUI optionsGUI, OptionPage page) {
        if (page.getName().contains(Text.translatable("options.iris.shaderPackSelection"))) {
            var shaderPackList = Option.<String>createBuilder()
                    .name(Text.translatable("options.iris.selectedShaderPack"))
                    .description(OptionDescription.of(Text.translatable("options.iris.selectedShaderPack.description")))
                    .binding(Iris.getIrisConfig().getShaderPackName().orElse("N/A"),
                            () -> Iris.getIrisConfig().getShaderPackName().orElse("N/A"), (val) -> {
                                if (val.equals("N/A"))
                                    val = null;
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
                            return new DropdownStringControllerBuilderImpl(opt).allowAnyValue(false).values(
                                    Iris.getShaderpacksDirectoryManager().enumerate());
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
                    .controller((opt) -> BooleanControllerBuilder.create(opt).coloured(true).trueFalseFormatter())
                    .build();

            return Optional.of(ConfigCategory.createBuilder()
                    .name(Text.literal("Shader Packs"))
                    .option(ButtonOption.createBuilder()
                            .name(Text.translatable("options.iris.openShaderPackScreen"))
                            .description(OptionDescription
                                    .of(Text.translatable("options.iris.openShaderPackScreen.description")))
                            .action((screen, opt) -> MinecraftClient.getInstance()
                                    .setScreen(new ShaderPackScreen(screen)))
                            .build())
                    .option(LabelOption.create(Text.empty()))
                    .option(ButtonOption.createBuilder()
                            .name(Text.translatable("options.iris.downloadShaders"))
                            .description(
                                    OptionDescription.of(Text.translatable("options.iris.downloadShaders.description")))
                            .action((screen, opt) -> MinecraftClient.getInstance()
                                    .setScreen(new ConfirmLinkScreen((bl) -> {
                                        if (bl) {
                                            Util.getOperatingSystem().open("https://modrinth.com/shaders");
                                        }

                                        MinecraftClient.getInstance().setScreen(screen);
                                    }, "https://modrinth.com/shaders", true)))
                            .build())
                    .option(ButtonOption.createBuilder()
                            .name(Text.translatable("options.iris.openShaderPackFolder"))
                            .description(OptionDescription
                                    .of(Text.translatable("options.iris.openShaderPacksFolder.description")))
                            .action((screen, opt) -> Util.getOperatingSystem()
                                    .open(FabricLoader.getInstance().getGameDir().resolve("shaderpacks/")))
                            .build())
                    .option(LabelOption.create(Text.translatable("options.iris.shaderPackOptions")))
                    .option(enableShaders)
                    .option(shaderPackList)
                    .build());
        }

        return Optional.empty();
    }

    public interface ShaderPageHolder {
        OptionPage getShaderPage();
    }
}
