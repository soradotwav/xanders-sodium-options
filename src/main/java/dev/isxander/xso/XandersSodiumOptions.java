package dev.isxander.xso;

import dev.isxander.xso.compat.*;
import dev.isxander.xso.config.XsoConfig;
import dev.isxander.xso.utils.DonationPrompt;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.impl.controller.EnumControllerBuilderImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.config.structure.EnumOption;
import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.data.fingerprint.HashedFingerprint;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class XandersSodiumOptions {
    private static boolean errorOccurred = false;

    public static Screen wrapSodiumScreen(VideoSettingsScreen videoSettingsScreen, List<OptionPage> pages,
            Screen prevScreen) {
        try {
            YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                    .title(Text.translatable("options.videoTitle"));

            AtomicReference<PlaceholderCategory> shaderPackPage = new AtomicReference<>();
            for (OptionPage page : pages) {
                var category = convertCategory(page);

                if (category == null)
                    continue;
                if (category instanceof PlaceholderCategory placeholderCategory) {
                    shaderPackPage.set(placeholderCategory);
                    continue;
                }

                builder.category(category);
            }

            builder.category(XsoConfig.getConfigCategory());

            if (shaderPackPage.get() != null) {
                builder.category(shaderPackPage.get());
            }

            builder.save(() -> {
                net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.applyAllOptions();
                XsoConfig.INSTANCE.save();
            });

            var options = SodiumClientMod.options();
            if (!options.notifications.hasSeenDonationPrompt) {
                HashedFingerprint fingerprint = null;

                try {
                    fingerprint = HashedFingerprint.loadFromDisk();
                } catch (Throwable var5) {
                    SodiumClientMod.logger().error("Failed to read the fingerprint from disk", var5);
                }

                if (fingerprint != null) {
                    Instant now = Instant.now();
                    Instant threshold = Instant.ofEpochSecond(fingerprint.timestamp()).plus(3L, ChronoUnit.DAYS);
                    if (now.isAfter(threshold)) {
                        options.notifications.hasSeenDonationPrompt = true;
                        try {
                            SodiumOptions.writeToDisk(options);
                        } catch (IOException var4) {
                            SodiumClientMod.logger().error("Failed to update config file", var4);
                        }
                        return new DonationPrompt(builder.build().generateScreen(prevScreen));
                    }
                }
            }

            return builder.build().generateScreen(prevScreen);
        } catch (Exception e) {
            var exception = new IllegalStateException("Failed to convert Sodium option screen to YACL with XSO!", e);

            if (XsoConfig.INSTANCE.instance().hardCrash) {
                throw exception;
            } else {
                exception.printStackTrace();

                return new NoticeScreen(() -> {
                    errorOccurred = true;
                    MinecraftClient.getInstance().setScreen(videoSettingsScreen);
                    errorOccurred = false;
                }, Text.literal("Xander's Sodium Options failed"), Text.literal(
                        "Whilst trying to convert Sodium's GUI to YACL with XSO mod, an error occurred which prevented the conversion. This is most likely due to a third-party mod adding its own settings to Sodium's screen. XSO will now display the original GUI.\n\nThe error has been logged to latest.log file."),
                        ScreenTexts.PROCEED, true);
            }
        }
    }

    @Nullable
    private static ConfigCategory convertCategory(OptionPage page) {
        try {
            if (Compat.IRIS) {
                Optional<ConfigCategory> shaderPackPage = IrisCompat.replaceShaderPackPage(page);
                if (shaderPackPage.isPresent()) {
                    return shaderPackPage.get();
                }
            }

            if (page.name().contains(Text.literal("LambDynamicLights"))) {
                return null;
            }

            ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
                    .name(page.name());

            for (var group : page.groups()) {
                categoryBuilder.option(LabelOption.create(Text.empty()));

                for (var option : group.options()) {
                    categoryBuilder.option(convertOption(option));
                }
            }

            if (Compat.MORE_CULLING)
                MoreCullingCompat.extendMoreCullingPage(page, categoryBuilder);

            return categoryBuilder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert Sodium option page named '" + page.name().getString()
                    + "' to YACL config category.", e);
        }
    }

    private static dev.isxander.yacl3.api.Option<?> convertOption(
            net.caffeinemc.mods.sodium.client.config.structure.Option sodiumOption) {
        try {
            return switch (sodiumOption) {
                case BooleanOption booleanOption -> convertBooleanOption(booleanOption);
                case IntegerOption integerOption -> convertIntegerOption(integerOption);
                case EnumOption<?> enumOption -> convertEnumOption(enumOption);
                default ->
                    throw new IllegalStateException(
                            "Unsupported Sodium Option type: " + sodiumOption.getClass().getName());
            };
        } catch (Exception e) {
            if (XsoConfig.INSTANCE.instance().lenientOptions) {
                e.printStackTrace();
                return ButtonOption.createBuilder()
                        .name(sodiumOption.getName())
                        .description(OptionDescription.of(sodiumOption.getTooltip(),
                                Text.translatable("xso.incompatible.tooltip").formatted(Formatting.RED)))
                        .available(false)
                        .text(Text.translatable("xso.incompatible.button").formatted(Formatting.RED))
                        .action((screen, opt) -> {
                        })
                        .build();
            } else {
                throw new IllegalStateException("Failed to convert Sodium option named '"
                        + sodiumOption.getName().getString() + "' to a YACL option!", e);
            }
        }
    }

    private static Option<Boolean> convertBooleanOption(BooleanOption option) {
        MutableText descText = option.getTooltip().copy();

        if (option.getImpact() != null) {
            descText = descText.append("\n")
                    .append(Text.translatable("sodium.options.performance_impact_string", option.getImpact().getName())
                            .formatted(Formatting.GRAY));
        }

        return dev.isxander.yacl3.api.Option.<Boolean>createBuilder()
                .name(option.getName())
                .description(OptionDescription.of(descText))
                .binding(new SodiumBinding<>(option))
                .available(option.isEnabled())
                .flags(convertFlags(option))
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Integer> convertIntegerOption(IntegerOption option) {
        MutableText descText = option.getTooltip().copy();

        if (option.getImpact() != null) {
            descText = descText.append("\n")
                    .append(Text.translatable("sodium.options.performance_impact_string", option.getImpact().getName())
                            .formatted(Formatting.GRAY));
        }

        var validator = option.getSteppedValidator();

        return dev.isxander.yacl3.api.Option.<Integer>createBuilder()
                .name(option.getName())
                .description(OptionDescription.of(descText))
                .binding(new SodiumBinding<>(option))
                .available(option.isEnabled())
                .flags(convertFlags(option))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(validator.min(), validator.max())
                        .step(validator.step())
                        .formatValue(option::formatValue))
                .build();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <E extends Enum<E>> Option<E> convertEnumOption(EnumOption<E> option) {
        MutableText descText = option.getTooltip().copy();

        if (option.getImpact() != null) {
            descText = descText.append("\n")
                    .append(Text.translatable("sodium.options.performance_impact_string", option.getImpact().getName())
                            .formatted(Formatting.GRAY));
        }

        return dev.isxander.yacl3.api.Option.<E>createBuilder()
                .name(option.getName())
                .description(OptionDescription.of(descText))
                .binding(new SodiumBinding<>(option))
                .available(option.isEnabled())
                .flags(convertFlags(option))
                .controller(opt -> new EnumControllerBuilderImpl<>((Option) opt)
                        .formatValue(value -> option.getElementName((E) value))
                        .enumClass(option.getEnumClass()))
                .build();
    }

    private static List<OptionFlag> convertFlags(net.caffeinemc.mods.sodium.client.config.structure.Option option) {
        List<OptionFlag> flags = new ArrayList<>();
        var sodiumFlags = option.getFlags();

        if (sodiumFlags == null) {
            return flags;
        }

        if (sodiumFlags
                .contains(net.caffeinemc.mods.sodium.api.config.option.OptionFlag.REQUIRES_RENDERER_RELOAD.getId())) {
            flags.add(OptionFlag.RELOAD_CHUNKS);
        } else if (sodiumFlags
                .contains(net.caffeinemc.mods.sodium.api.config.option.OptionFlag.REQUIRES_RENDERER_UPDATE.getId())) {
            flags.add(OptionFlag.WORLD_RENDER_UPDATE);
        }
        if (sodiumFlags
                .contains(net.caffeinemc.mods.sodium.api.config.option.OptionFlag.REQUIRES_ASSET_RELOAD.getId())) {
            flags.add(OptionFlag.ASSET_RELOAD);
        }
        if (sodiumFlags
                .contains(net.caffeinemc.mods.sodium.api.config.option.OptionFlag.REQUIRES_GAME_RESTART.getId())) {
            flags.add(OptionFlag.GAME_RESTART);
        }

        return flags;
    }

    public static boolean shouldConvertGui() {
        return XsoConfig.INSTANCE.instance().enabled && !errorOccurred;
    }
}
