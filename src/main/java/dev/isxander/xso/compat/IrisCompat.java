package dev.isxander.xso.compat;

import dev.isxander.xso.XandersSodiumOptions;
import dev.isxander.xso.config.XsoConfig;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.impl.controller.DropdownStringControllerBuilderImpl;
import java.io.IOException;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

//? fabric {

import net.fabricmc.loader.api.FabricLoader;
 
//?} elif neoforge {
/*import net.neoforged.fml.loading.FMLPaths;
*///?}

public class IrisCompat {
    private static boolean dirty = false;

    public static ConfigCategory createShaderPacksCategory(Screen prevScreen, VideoSettingsScreen videoSettingsScreen) {
        if (XsoConfig.INSTANCE.instance().externalMenus) {
            return PlaceholderCategory.createBuilder()
                    .name(Component.translatable("options.iris.shaderPackSelection.title"))
                    .screen((client, screen) -> {
                        try {
                            return new ShaderPackScreen(new Screen(Component.empty()) {
                                @Override
                                protected void init() {
                                    minecraft.setScreen(XandersSodiumOptions.wrapSodiumScreen(
                                            videoSettingsScreen, ConfigManager.CONFIG.getModOptions(), prevScreen));
                                }
                            });
                        } catch (Exception e) {
                            XandersSodiumOptions.LOGGER.error("Failed to open Iris settings screen", e);

                            return new net.minecraft.client.gui.screens.AlertScreen(
                                    () -> client.setScreen(null),
                                    Component.literal("Iris Integration Error"),
                                    Component.literal("Xander's Sodium Options failed to open Iris settings screen.\n\n"
                                            + e.getMessage()));
                        }
                    })
                    .build();
        }

        var shaderPackList = Option.<String>createBuilder()
                .name(Component.translatable("options.iris.selectedShaderPack"))
                .description(
                        OptionDescription.of(Component.translatable("options.iris.selectedShaderPack.description")))
                .binding(
                        Iris.getIrisConfig().getShaderPackName().orElse(""),
                        () -> Iris.getIrisConfig().getShaderPackName().orElse(""),
                        (val) -> {
                            if (val.isEmpty()) val = null;
                            Iris.getIrisConfig().setShaderPackName(val);
                            dirty = true;
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
                .name(Component.translatable("options.iris.enableShaders"))
                .description(OptionDescription.of(Component.translatable("options.iris.enableShaders.description")))
                .binding(true, () -> Iris.getIrisConfig().areShadersEnabled(), (val) -> {
                    Iris.getIrisConfig().setShadersEnabled(val);
                    dirty = true;
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
                .name(Component.translatable("options.iris.shaderPackSelection.title"))
                .option(LabelOption.create(Component.empty()))
                .option(ButtonOption.createBuilder()
                        .name(Component.translatable("options.iris.openShaderPackScreen"))
                        .text(Component.literal("➔"))
                        .description(OptionDescription.of(
                                Component.translatable("options.iris.openShaderPackScreen.description")))
                        .action((screen, opt) -> Minecraft.getInstance().setScreen(new ShaderPackScreen(screen)))
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Component.translatable("options.iris.downloadShaders"))
                        .text(Component.literal("➔"))
                        .description(OptionDescription.of(
                                Component.translatable("options.iris.downloadShaders.description")))
                        .action((screen, opt) -> Minecraft.getInstance()
                                .setScreen(new ConfirmLinkScreen(
                                        (bl) -> {
                                            if (bl) {
                                                Util.getPlatform().openUri("https://modrinth.com/shaders");
                                            }
                                            Minecraft.getInstance().setScreen(screen);
                                        },
                                        "https://modrinth.com/shaders",
                                        true)))
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Component.translatable("options.iris.openShaderPackFolder"))
                        .text(Component.literal("➔"))
                        .description(OptionDescription.of(
                                Component.translatable("options.iris.openShaderPacksFolder.description")))
                        .action((screen, opt) -> Util.getPlatform()
                                .openPath(
                                        //? fabric {
                                        
                                        FabricLoader.getInstance().getGameDir().resolve("shaderpacks/")))
                                         
                                        //?} elif neoforge {
                                        /*FMLPaths.GAMEDIR.get().resolve("shaderpacks/")))
                                        *///?}
                        .build())
                .option(LabelOption.create(Component.translatable("options.iris.shaderPackOptions")))
                .option(enableShaders)
                .option(shaderPackList)
                .build();
    }

    public static void applyChanges() {
        if (dirty) {
            try {
                Iris.getIrisConfig().save();
                Iris.reload();
                dirty = false;
            } catch (IOException e) {
                XandersSodiumOptions.LOGGER.error("Failed to save Iris config", e);
            }
        }
    }
}
