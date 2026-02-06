package dev.isxander.xso;

import dev.isxander.xso.compat.*;
import dev.isxander.xso.config.XsoConfig;
import dev.isxander.xso.mixins.CyclingControlAccessor;
import dev.isxander.xso.mixins.DynamicMaxSliderControlAccessor;
import dev.isxander.xso.mixins.OptionImplAccessor;
import dev.isxander.xso.mixins.SliderControlAccessor;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.impl.controller.EnumControllerBuilderImpl;
import java.util.*;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.TextProvider;
import net.caffeinemc.mods.sodium.client.gui.options.control.CyclingControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.DynamicMaxSliderControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TranslatableOption;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XandersSodiumOptions {
    private static boolean errorOccurred = false;
    private static final Logger LOGGER = LoggerFactory.getLogger("xanders-sodium-options");

    public static Screen wrapSodiumScreen(
            SodiumOptionsGUI sodiumOptionsGUI, List<OptionPage> pages, Screen prevScreen) {
        try {
            YetAnotherConfigLib.Builder builder =
                    YetAnotherConfigLib.createBuilder().title(Text.translatable("options.videoTitle"));

            List<ConfigCategory> categories = new ArrayList<>();
            for (OptionPage page : pages) {
                var category = convertCategory(page);

                if (category == null) continue;

                categories.add(category);
            }

            if (Compat.IRIS) {
                int insertPosition = Math.min(4, categories.size());
                categories.add(insertPosition, IrisCompat.createShaderPacksCategory());
            }

            categories.add(XsoConfig.getConfigCategory());

            for (ConfigCategory category : categories) {
                builder.category(category);
            }

            builder.save(() -> {
                Set<OptionStorage<?>> storages = new HashSet<>();
                pages.stream().flatMap(s -> s.getOptions().stream()).forEach(opt -> storages.add(opt.getStorage()));
                storages.forEach(OptionStorage::save);

                XsoConfig.INSTANCE.save();
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
                            MinecraftClient.getInstance().setScreen(sodiumOptionsGUI);
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
    private static ConfigCategory convertCategory(OptionPage page) {
        try {
            if (Compat.IRIS) {
                if (IrisCompat.isIrisSettingsPage(page.getName())
                        || page.getName().contains(Text.translatable("options.iris.shaderPackSelection"))) {
                    return null;
                }
            }

            if (page.getName().contains(Text.literal("LambDynamicLights"))) {
                return null;
            }

            ConfigCategory.Builder categoryBuilder =
                    ConfigCategory.createBuilder().name(page.getName());

            for (var group : page.getGroups()) {
                categoryBuilder.option(LabelOption.create(Text.empty()));

                for (var option : group.getOptions()) {
                    categoryBuilder.option(convertOption(option));
                }
            }

            if (Compat.MORE_CULLING) MoreCullingCompat.extendMoreCullingPage(page, categoryBuilder);

            return categoryBuilder.build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to convert Sodium option page named '"
                            + page.getName().getString() + "' to YACL config category.",
                    e);
        }
    }

    private static <T> Option<?> convertOption(net.caffeinemc.mods.sodium.client.gui.options.Option<T> sodiumOption) {
        try {
            MutableText descText = sodiumOption.getTooltip().copy();

            Option.Builder<T> builder = Option.<T>createBuilder()
                    .name(sodiumOption.getName())
                    .flags(convertFlags(sodiumOption))
                    .binding(
                            Compat.MORE_CULLING
                                    ? MoreCullingCompat.getBinding(sodiumOption)
                                    : new SodiumBinding<>(sodiumOption))
                    .available(sodiumOption.isAvailable());

            if (sodiumOption.getImpact() != null) {
                descText = descText.append("\n")
                        .append(Text.translatable(
                                        "sodium.options.performance_impact_string",
                                        sodiumOption.getImpact().getLocalizedName())
                                .formatted(Formatting.GRAY));
            }

            builder.description(OptionDescription.of(descText));

            addController(builder, sodiumOption);

            Option<T> built = builder.build();
            if (Compat.MORE_CULLING) MoreCullingCompat.addAvailableCheck(built, sodiumOption);
            return built;
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
                                + sodiumOption.getName().getString() + "' to a YACL option!",
                        e);
            }
        }
    }

    // nasty, nasty raw types to make the compiler not commit die
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> void addController(
            dev.isxander.yacl3.api.Option.Builder<T> yaclOption,
            net.caffeinemc.mods.sodium.client.gui.options.Option<T> sodiumOption) {
        if (sodiumOption.getControl() instanceof TickBoxControl) {
            yaclOption.controller(opt -> (dev.isxander.yacl3.api.controller.ControllerBuilder<T>)
                    TickBoxControllerBuilder.create((Option<Boolean>) opt));
            return;
        }

        if (sodiumOption.getControl() instanceof CyclingControl cyclingControl) {
            var allowedValues = ((CyclingControlAccessor<?>) cyclingControl).getAllowedValues();

            Class<?> arrType = allowedValues.getClass().getComponentType();

            yaclOption.controller(opt -> new EnumControllerBuilderImpl<>((Option) opt)
                    .formatValue(value -> {
                        if (value instanceof TextProvider textProvider) return textProvider.getLocalizedName();
                        if (value instanceof TranslatableOption translatableOption) return translatableOption.getText();
                        return Text.of(((Enum<?>) value).name());
                    })
                    .enumClass(arrType));
            return;
        }

        if (sodiumOption.getControl() instanceof DynamicMaxSliderControl dynamicMaxSliderControl) {
            DynamicMaxSliderControlAccessor accessor = (DynamicMaxSliderControlAccessor) dynamicMaxSliderControl;
            addSliderController(
                    yaclOption,
                    sodiumOption,
                    accessor.getMin(),
                    accessor.getMax().getAsInt(),
                    accessor.getInterval(),
                    accessor.getMode());
            return;
        }

        if (Compat.SODIUM_EXTRA && SodiumExtraCompat.convertControl(yaclOption, sodiumOption)) {
            return;
        }

        if (sodiumOption.getControl() instanceof SliderControl sliderControl) {
            SliderControlAccessor accessor = (SliderControlAccessor) sliderControl;
            addSliderController(
                    yaclOption,
                    sodiumOption,
                    accessor.getMin(),
                    accessor.getMax(),
                    accessor.getInterval(),
                    accessor.getMode());
            return;
        }

        throw new IllegalStateException("Unsupported Sodium Controller: "
                + sodiumOption.getControl().getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static <T> void addSliderController(
            dev.isxander.yacl3.api.Option.Builder<T> yaclOption,
            net.caffeinemc.mods.sodium.client.gui.options.Option<T> sodiumOption,
            int min,
            int max,
            int interval,
            net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatter mode) {
        T initialValue = null;
        if (Compat.MORE_CULLING && MoreCullingCompat.isMoreCullingOption(sodiumOption)) {
            initialValue = MoreCullingCompat.getBinding(sodiumOption).getValue();
        } else if (sodiumOption instanceof net.caffeinemc.mods.sodium.client.gui.options.OptionImpl) {
            initialValue = ((OptionImplAccessor<Object, T>) sodiumOption)
                    .getBinding()
                    .getValue(sodiumOption.getStorage().getData());
        }
        if (initialValue instanceof Float) {
            yaclOption.controller(opt -> (dev.isxander.yacl3.api.controller.ControllerBuilder<T>)
                    FloatSliderControllerBuilder.create((Option<Float>) opt)
                            .range((float) min, (float) max)
                            .step((float) interval)
                            .formatValue(v -> mode.format(v.intValue())));
        } else if (initialValue instanceof Double) {
            yaclOption.controller(opt -> (dev.isxander.yacl3.api.controller.ControllerBuilder<T>)
                    DoubleSliderControllerBuilder.create((Option<Double>) opt)
                            .range((double) min, (double) max)
                            .step((double) interval)
                            .formatValue(v -> mode.format(v.intValue())));
        } else {
            yaclOption.controller(opt -> (dev.isxander.yacl3.api.controller.ControllerBuilder<T>)
                    IntegerSliderControllerBuilder.create((Option<Integer>) opt)
                            .range(min, max)
                            .step(interval)
                            .formatValue(mode::format));
        }
    }

    private static List<OptionFlag> convertFlags(net.caffeinemc.mods.sodium.client.gui.options.Option<?> sodiumOption) {
        List<OptionFlag> flags = new ArrayList<>();

        if (sodiumOption
                .getFlags()
                .contains(net.caffeinemc.mods.sodium.client.gui.options.OptionFlag.REQUIRES_RENDERER_RELOAD)) {
            flags.add(OptionFlag.RELOAD_CHUNKS);
        } else if (sodiumOption
                .getFlags()
                .contains(net.caffeinemc.mods.sodium.client.gui.options.OptionFlag.REQUIRES_RENDERER_UPDATE)) {
            flags.add(OptionFlag.WORLD_RENDER_UPDATE);
        }

        if (sodiumOption
                .getFlags()
                .contains(net.caffeinemc.mods.sodium.client.gui.options.OptionFlag.REQUIRES_ASSET_RELOAD)) {
            flags.add(OptionFlag.ASSET_RELOAD);
        }

        if (sodiumOption
                .getFlags()
                .contains(net.caffeinemc.mods.sodium.client.gui.options.OptionFlag.REQUIRES_GAME_RESTART)) {
            flags.add(OptionFlag.GAME_RESTART);
        }

        return flags;
    }

    public static boolean shouldConvertGui() {
        return XsoConfig.INSTANCE.instance().enabled && !errorOccurred;
    }
}
