package dev.isxander.xso;

import static net.caffeinemc.mods.sodium.api.config.option.OptionFlag.*;

import dev.isxander.xso.compat.*;
import dev.isxander.xso.config.XsoConfig;
import dev.isxander.xso.mixins.SodiumOptionAccessor;
import dev.isxander.xso.utils.CategoryDescriptions;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.impl.controller.EnumControllerBuilderImpl;
import java.util.*;
import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.caffeinemc.mods.sodium.client.config.structure.EnumOption;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalButtonOption;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.config.structure.Page;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XandersSodiumOptions {
    private static boolean errorOccurred = false;
    private static final String SODIUM_CONFIG_ID = "sodium";
    public static final Logger LOGGER = LoggerFactory.getLogger("xanders-sodium-options");

    public static Screen wrapSodiumScreen(
            VideoSettingsScreen videoSettingsScreen, List<ModOptions> modOptionsList, Screen prevScreen) {
        try {
            YetAnotherConfigLib.Builder builder =
                    YetAnotherConfigLib.createBuilder().title(Text.translatable("options.videoTitle"));

            CategoryDescriptions.clearRegistrations();

            List<ConfigCategory> categories = new ArrayList<>();
            List<ModOptions> thirdPartyMods = new ArrayList<>();

            for (ModOptions mod : modOptionsList) {
                if (SODIUM_CONFIG_ID.equals(mod.configId())) {
                    for (Page page : mod.pages()) {
                        if (page instanceof OptionPage optionPage) {
                            var category = convertSodiumCategory(optionPage);
                            if (category != null) {
                                categories.add(category);
                            }
                        }
                    }
                } else {
                    thirdPartyMods.add(mod);
                }
            }

            if (Compat.IRIS) {
                var irisCategory = IrisCompat.createShaderPacksCategory(prevScreen, videoSettingsScreen);
                CategoryDescriptions.registerCategoryModId(irisCategory.name().getString(), "iris");
                categories.add(irisCategory);
            }

            if (Compat.LAMBDYNAMICLIGHTS) {
                var lambdCategory = LDLCompat.createLdcCategory(prevScreen, videoSettingsScreen);
                CategoryDescriptions.registerCategoryModId(lambdCategory.name().getString(), "lambdynlights");
                categories.add(lambdCategory);
            }

            for (ModOptions mod : thirdPartyMods) {
                if (Compat.IRIS && "iris".equals(mod.configId())) {
                    continue;
                }

                if (Compat.LAMBDYNAMICLIGHTS && "lambdynlights".equals(mod.configId())) {
                    continue;
                }

                var category = convertModCategory(mod);
                if (category != null) {
                    CategoryDescriptions.registerCategoryModId(category.name().getString(), mod.configId());
                    categories.add(category);
                }
            }

            var xsoCategory = XsoConfig.getConfigCategory();
            CategoryDescriptions.registerCategoryModId(xsoCategory.name().getString(), "xanders-sodium-options");
            categories.add(xsoCategory);

            for (ConfigCategory category : categories) {
                builder.category(category);
            }

            builder.save(() -> {
                net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.applyAllOptions();
                XsoConfig.INSTANCE.save();
                LDLCompat.save();
            });
            return builder.build().generateScreen(prevScreen);
        } catch (Exception e) {
            var exception = new IllegalStateException("Failed to convert Sodium option screen to YACL with XSO!", e);

            if (XsoConfig.INSTANCE.instance().hardCrash) {
                throw exception;
            } else {
                LOGGER.error("Failed to convert Sodium GUI to YACL", exception);

                return new NoticeScreen(
                        () -> {
                            errorOccurred = true;
                            MinecraftClient.getInstance().setScreen(videoSettingsScreen);
                            errorOccurred = false;
                        },
                        Text.literal("Xander's Sodium Options failed"),
                        Text.literal(
                                "Whilst trying to convert Sodium's GUI to YACL with XSO mod, an error occurred which prevented the conversion. This is most likely due to a third-party mod adding its own settings to Sodium's screen. XSO will now display the original GUI.\n\nThe error has been logged to latest.log file."),
                        ScreenTexts.PROCEED,
                        true);
            }
        }
    }

    @Nullable
    private static ConfigCategory convertSodiumCategory(OptionPage page) {
        try {
            ConfigCategory.Builder categoryBuilder =
                    ConfigCategory.createBuilder().name(page.name());

            Map<dev.isxander.yacl3.api.Option<?>, net.caffeinemc.mods.sodium.client.config.structure.Option> optionMap =
                    new LinkedHashMap<>();

            for (var group : page.groups()) {
                categoryBuilder.option(LabelOption.create(Text.empty()));

                for (var option : group.options()) {
                    var yaclOption = convertOption(option);
                    optionMap.put(yaclOption, option);
                    categoryBuilder.option(yaclOption);
                }
            }

            wireAvailabilityListeners(optionMap);

            return categoryBuilder.build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to convert Sodium option page named '"
                            + page.name().getString()
                            + "' to YACL config category.",
                    e);
        }
    }

    @Nullable
    private static ConfigCategory convertModCategory(ModOptions mod) {
        try {
            List<OptionPage> optionPages = mod.pages().stream()
                    .filter(page -> page instanceof OptionPage)
                    .map(page -> (OptionPage) page)
                    .toList();

            if (optionPages.isEmpty()) {
                return null;
            }

            ConfigCategory.Builder categoryBuilder =
                    ConfigCategory.createBuilder().name(Text.literal(mod.name()));

            Map<dev.isxander.yacl3.api.Option<?>, net.caffeinemc.mods.sodium.client.config.structure.Option> optionMap =
                    new LinkedHashMap<>();

            if (optionPages.size() == 1) {
                OptionPage page = optionPages.getFirst();
                for (var group : page.groups()) {
                    categoryBuilder.option(LabelOption.create(Text.empty()));

                    for (var option : group.options()) {
                        var yaclOption = convertOption(option);
                        optionMap.put(yaclOption, option);
                        categoryBuilder.option(yaclOption);
                    }
                }
            } else {
                OptionGroup.Builder firstGroupBuilder = null;

                for (OptionPage page : optionPages) {
                    OptionGroup.Builder groupBuilder =
                            OptionGroup.createBuilder().name(page.name()).collapsed(false);

                    if (firstGroupBuilder == null) {
                        firstGroupBuilder = groupBuilder;
                    }

                    boolean first = true;
                    for (var group : page.groups()) {
                        if (!first) {
                            groupBuilder.option(LabelOption.create(Text.empty()));
                        }
                        first = false;

                        for (var option : group.options()) {
                            var yaclOption = convertOption(option);
                            optionMap.put(yaclOption, option);
                            groupBuilder.option(yaclOption);
                        }
                    }

                    if (Compat.MORE_CULLING
                            && "moreculling".equals(mod.configId())
                            && groupBuilder == firstGroupBuilder) {
                        MoreCullingCompat.addResetCacheButton(groupBuilder);
                    }

                    categoryBuilder.group(groupBuilder.build());
                }
            }

            wireAvailabilityListeners(optionMap);

            return categoryBuilder.build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to convert mod '" + mod.name() + "' options to YACL config category.", e);
        }
    }

    private static dev.isxander.yacl3.api.Option<?> convertOption(
            net.caffeinemc.mods.sodium.client.config.structure.Option sodiumOption) {
        try {
            return switch (sodiumOption) {
                case BooleanOption booleanOption -> convertBooleanOption(booleanOption);
                case IntegerOption integerOption -> convertIntegerOption(integerOption);
                case EnumOption<?> enumOption -> convertEnumOption(enumOption);
                case ExternalButtonOption buttonOption -> convertExternalButtonOption(buttonOption);
                default -> throw new IllegalStateException("Unsupported Sodium Option type: "
                        + sodiumOption.getClass().getName());
            };
        } catch (Exception e) {
            if (XsoConfig.INSTANCE.instance().lenientOptions) {
                LOGGER.error(
                        "Failed to convert Sodium option named '{}' to YACL option.",
                        sodiumOption.getName().getString(),
                        e);

                return ButtonOption.createBuilder()
                        .name(sodiumOption.getName())
                        .description(OptionDescription.of(
                                sodiumOption.getTooltip(),
                                Text.translatable("xso.incompatible.tooltip").formatted(Formatting.RED)))
                        .available(false)
                        .text(Text.translatable("xso.incompatible.button").formatted(Formatting.RED))
                        .action((screen, opt) -> {})
                        .build();
            } else {
                throw new IllegalStateException(
                        "Failed to convert Sodium option named '"
                                + sodiumOption.getName().getString()
                                + "' to a YACL option!",
                        e);
            }
        }
    }

    private static Option<Boolean> convertBooleanOption(BooleanOption option) {
        MutableText descText = option.getTooltip().copy();

        if (option.getImpact() != null) {
            descText = descText.append("\n")
                    .append(Text.translatable(
                                    "sodium.options.performance_impact_string",
                                    option.getImpact().getName())
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
                    .append(Text.translatable(
                                    "sodium.options.performance_impact_string",
                                    option.getImpact().getName())
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <E extends Enum<E>> Option<E> convertEnumOption(EnumOption<E> option) {
        MutableText descText = option.getTooltip().copy();

        if (option.getImpact() != null) {
            descText = descText.append("\n")
                    .append(Text.translatable(
                                    "sodium.options.performance_impact_string",
                                    option.getImpact().getName())
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

    private static ButtonOption convertExternalButtonOption(ExternalButtonOption option) {
        return ButtonOption.createBuilder()
                .name(option.getName())
                .description(OptionDescription.of(option.getTooltip()))
                .available(option.isEnabled())
                .text(Text.literal("➔"))
                .action((screen, opt) -> option.getCurrentScreenConsumer().accept(screen))
                .build();
    }

    private static List<OptionFlag> convertFlags(net.caffeinemc.mods.sodium.client.config.structure.Option option) {
        List<OptionFlag> flags = new ArrayList<>();
        var sodiumFlags = option.getFlags();

        if (sodiumFlags == null) {
            return flags;
        }

        if (sodiumFlags.contains(REQUIRES_RENDERER_RELOAD.getId())) {
            flags.add(OptionFlag.RELOAD_CHUNKS);
        } else if (sodiumFlags.contains(REQUIRES_RENDERER_UPDATE.getId())) {
            flags.add(OptionFlag.WORLD_RENDER_UPDATE);
        }
        if (sodiumFlags.contains(REQUIRES_ASSET_RELOAD.getId())) {
            flags.add(OptionFlag.ASSET_RELOAD);
        }
        if (sodiumFlags.contains(REQUIRES_GAME_RESTART.getId())) {
            flags.add(OptionFlag.GAME_RESTART);
        }

        return flags;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void wireAvailabilityListeners(
            Map<dev.isxander.yacl3.api.Option<?>, net.caffeinemc.mods.sodium.client.config.structure.Option>
                    optionMap) {
        Map<
                        Identifier,
                        Map.Entry<
                                dev.isxander.yacl3.api.Option<?>,
                                net.caffeinemc.mods.sodium.client.config.structure.Option>>
                byId = new HashMap<>();
        for (var entry : optionMap.entrySet()) {
            var id = ((SodiumOptionAccessor) entry.getValue()).getId();
            if (id != null) {
                byId.put(id, entry);
            }
        }

        for (var entry : optionMap.entrySet()) {
            var sodiumDependent = entry.getValue();
            var dependencyIds = sodiumDependent.getEnabled().getDependencies();
            if (dependencyIds == null || dependencyIds.isEmpty()) continue;

            var dependentYacl = entry.getKey();
            for (var depId : dependencyIds) {
                var controllerEntry = byId.get(depId);
                if (controllerEntry == null) continue;

                var controllerYacl = controllerEntry.getKey();
                var controllerSodium = controllerEntry.getValue();

                controllerYacl.addEventListener((opt, event) -> {
                    if (event == OptionEventListener.Event.STATE_CHANGE) {
                        if (opt.pendingValue() instanceof Boolean bool) {
                            dependentYacl.setAvailable(bool);
                        } else if (controllerSodium instanceof StatefulOption stateful) {
                            var original = stateful.getValidatedValue();
                            stateful.modifyValue(opt.pendingValue());
                            dependentYacl.setAvailable(sodiumDependent.isEnabled());
                            stateful.modifyValue(original);
                        }
                    }
                });
            }
        }
    }

    public static boolean shouldConvertGui() {
        return XsoConfig.INSTANCE.instance().enabled && !errorOccurred;
    }
}
