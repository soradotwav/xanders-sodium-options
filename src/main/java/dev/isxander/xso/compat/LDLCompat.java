package dev.isxander.xso.compat;

import dev.isxander.xso.XandersSodiumOptions;
import dev.isxander.xso.config.XsoConfig;
import dev.isxander.xso.mixins.compat.DynamicLightsConfigAccessor;
import dev.isxander.xso.mixins.compat.SettingEntryAccessor;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.lambdaurora.lambdynlights.ChunkRebuildSchedulerMode;
import dev.lambdaurora.lambdynlights.DynamicLightsConfig;
import dev.lambdaurora.lambdynlights.DynamicLightsMode;
import dev.lambdaurora.lambdynlights.ExplosiveLightingMode;
import dev.lambdaurora.lambdynlights.LambDynLights;
import dev.lambdaurora.lambdynlights.accessor.DynamicLightHandlerHolder;
import dev.lambdaurora.lambdynlights.config.SettingEntry;
import dev.lambdaurora.lambdynlights.gui.SettingsScreen;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

@SuppressWarnings({"UnstableApiUsage"})
public class LDLCompat {
    private static boolean dirty = false;

    public static ConfigCategory createLdcCategory(Screen prevScreen, VideoSettingsScreen videoSettingsScreen) {
        if (XsoConfig.INSTANCE.instance().externalMenus) {
            return PlaceholderCategory.createBuilder()
                    .name(Component.translatable("lambdynlights"))
                    .screen((client, screen) -> {
                        try {
                            Screen proxy = new Screen(Component.empty()) {
                                @Override
                                protected void init() {
                                    minecraft.setScreen(XandersSodiumOptions.wrapSodiumScreen(
                                            videoSettingsScreen, ConfigManager.CONFIG.getModOptions(), prevScreen));
                                }
                            };
                            return new SettingsScreen(proxy);
                        } catch (Exception e) {
                            XandersSodiumOptions.LOGGER.error("Failed to open LambDynamicLights settings screen", e);

                            return new net.minecraft.client.gui.screens.AlertScreen(
                                    () -> client.setScreen(null),
                                    Component.literal("LambDynamicLights Integration Error"),
                                    Component.literal(
                                            "Xander's Sodium Options failed to open LambDynamicLights settings screen.\n\n"
                                                    + e.getMessage()));
                        }
                    })
                    .build();
        }

        DynamicLightsConfig config = LambDynLights.get().config;

        ConfigCategory.Builder builder = ConfigCategory.createBuilder().name(Component.translatable("lambdynlights"));

        OptionGroup.Builder generalGroup = OptionGroup.createBuilder()
                .name(Component.translatable("lambdynlights.menu.tabs.general"))
                .collapsed(false);

        generalGroup.option(Option.<DynamicLightsMode>createBuilder()
                .name(Component.translatable("lambdynlights.option.mode"))
                .binding(
                        DynamicLightsConfigAccessor.getDefaultDynamicLightsMode(),
                        config::getDynamicLightsMode,
                        mode -> {
                            config.setDynamicLightsMode(mode);
                            dirty = true;
                        })
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(DynamicLightsMode.class)
                        .formatValue(DynamicLightsMode::getTranslatedText))
                .build());

        generalGroup.option(createBooleanOption(config.getEntitiesLightSource(), "lambdynlights.tooltip.entities"));
        generalGroup.option(
                createBooleanOption(config.getSelfLightSource(), "lambdynlights.tooltip.self_light_source"));
        generalGroup.option(
                createBooleanOption(config.getWaterSensitiveCheck(), "lambdynlights.tooltip.water_sensitive"));

        builder.group(generalGroup.build());

        OptionGroup.Builder performanceGroup = OptionGroup.createBuilder()
                .name(Component.translatable("lambdynlights.menu.tabs.performance"))
                .collapsed(false);

        performanceGroup.option(Option.<ChunkRebuildSchedulerMode>createBuilder()
                .name(Component.translatable("lambdynlights.option.chunk_rebuild_scheduler"))
                .description(OptionDescription.of(Component.translatable(
                        "lambdynlights.option.chunk_rebuild_scheduler.tooltip",
                        ChunkRebuildSchedulerMode.CULLING.getTranslatedText(),
                        ChunkRebuildSchedulerMode.IMMEDIATE.getTranslatedText())))
                .binding(
                        DynamicLightsConfigAccessor.getDefaultChunkRebuildSchedulerMode(),
                        config::getChunkRebuildSchedulerMode,
                        mode -> {
                            config.setChunkRebuildSchedulerMode(mode);
                            dirty = true;
                        })
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(ChunkRebuildSchedulerMode.class)
                        .formatValue(ChunkRebuildSchedulerMode::getTranslatedText))
                .build());

        performanceGroup.option(createAdaptiveTickingOption("slow", config::getSlowTickingDistance, (val) -> {
            config.slowTickingOption.set((double) val);
            dirty = true;
        }));
        performanceGroup.option(createAdaptiveTickingOption("slower", config::getSlowerTickingDistance, (val) -> {
            config.slowerTickingOption.set((double) val);
            dirty = true;
        }));
        performanceGroup.option(createBooleanOption(
                config.getBackgroundAdaptiveTicking(),
                "lambdynlights.option.adaptive_ticking.background_sleep.tooltip"));

        builder.group(performanceGroup.build());

        OptionGroup.Builder entitiesGroup = OptionGroup.createBuilder()
                .name(Component.translatable("lambdynlights.menu.light_sources"))
                .collapsed(true);

        BuiltInRegistries.ENTITY_TYPE.stream()
                .map(DynamicLightHandlerHolder::cast)
                .forEach(holder -> {
                    var setting = holder.lambdynlights$getSetting();
                    if (setting != null) {
                        @SuppressWarnings("unchecked")
                        var binding = ((SettingEntryAccessor<Boolean>) (Object) setting).getDefaultValue();
                        entitiesGroup.option(Option.<Boolean>createBuilder()
                                .name(holder.lambdynlights$getName())
                                .binding(binding, setting::get, val -> {
                                    setting.set(val);
                                    dirty = true;
                                })
                                .controller(TickBoxControllerBuilder::create)
                                .build());
                    }
                });

        builder.group(entitiesGroup.build());

        OptionGroup.Builder specialGroup = OptionGroup.createBuilder()
                .name(Component.translatable("lambdynlights.menu.tabs.dynamic_lights.special"))
                .collapsed(false);

        specialGroup.option(Option.<ExplosiveLightingMode>createBuilder()
                .name(Component.translatable("entity.minecraft.creeper"))
                .description(OptionDescription.of(Component.translatable(
                        "lambdynlights.tooltip.creeper_lighting",
                        ExplosiveLightingMode.OFF.getTranslatedText(),
                        ExplosiveLightingMode.SIMPLE.getTranslatedText(),
                        ExplosiveLightingMode.FANCY.getTranslatedText())))
                .binding(
                        DynamicLightsConfigAccessor.getDefaultCreeperLightingMode(),
                        config::getCreeperLightingMode,
                        mode -> {
                            config.setCreeperLightingMode(mode);
                            dirty = true;
                        })
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(ExplosiveLightingMode.class)
                        .formatValue(ExplosiveLightingMode::getTranslatedText))
                .build());

        specialGroup.option(Option.<ExplosiveLightingMode>createBuilder()
                .name(Component.translatable("block.minecraft.tnt"))
                .description(OptionDescription.of(Component.translatable(
                        "lambdynlights.tooltip.tnt_lighting",
                        ExplosiveLightingMode.OFF.getTranslatedText(),
                        ExplosiveLightingMode.SIMPLE.getTranslatedText(),
                        ExplosiveLightingMode.FANCY.getTranslatedText())))
                .binding(DynamicLightsConfigAccessor.getDefaultTntLightingMode(), config::getTntLightingMode, mode -> {
                    config.setTntLightingMode(mode);
                    dirty = true;
                })
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(ExplosiveLightingMode.class)
                        .formatValue(ExplosiveLightingMode::getTranslatedText))
                .build());

        specialGroup.option(
                createBooleanOption(config.getBeamLighting(), "lambdynlights.option.light_sources.beam.tooltip"));
        specialGroup.option(
                createBooleanOption(config.getFireflyLighting(), "lambdynlights.option.light_sources.firefly.tooltip"));
        specialGroup.option(createBooleanOption(
                config.getGuardianLaser(), "lambdynlights.option.light_sources.guardian_laser.tooltip"));
        specialGroup.option(createBooleanOption(
                config.getSonicBoomLighting(), "lambdynlights.option.light_sources.sonic_boom.tooltip"));
        specialGroup.option(createBooleanOption(
                config.getGlowingEffectLighting(), "lambdynlights.option.light_sources.glowing_effect.tooltip"));

        builder.group(specialGroup.build());

        OptionGroup.Builder debugGroup = OptionGroup.createBuilder()
                .name(Component.translatable("lambdynlights.menu.tabs.debug"))
                .description(OptionDescription.of(Component.translatable("lambdynlights.menu.tabs.debug.description")))
                .collapsed(true);

        debugGroup.option(createBooleanOption(
                config.getDebugActiveDynamicLightingCells(),
                "lambdynlights.option.debug.active_dynamic_lighting_cells.tooltip"));

        debugGroup.option(Option.<Integer>createBuilder()
                .name(Component.translatable("lambdynlights.option.debug.cell_display_radius"))
                .binding(
                        DynamicLightsConfigAccessor.getDefaultDebugCellDisplayRadius(),
                        config::getDebugCellDisplayRadius,
                        val -> {
                            config.setDebugCellDisplayRadius(val);
                            dirty = true;
                        })
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(0, 10)
                        .step(1)
                        .formatValue(v -> v <= 0
                                ? Component.translatable("options.off").withStyle(ChatFormatting.RED)
                                : Component.literal(String.format("%d", v))))
                .build());

        debugGroup.option(createBooleanOption(
                config.getDebugDisplayDynamicLightingChunkRebuilds(),
                "lambdynlights.option.debug.display_dynamic_lighting_chunk_rebuild.tooltip"));

        debugGroup.option(Option.<Integer>createBuilder()
                .name(Component.translatable("lambdynlights.option.debug.light_level_radius"))
                .binding(
                        DynamicLightsConfigAccessor.getDefaultDebugLightLevelRadius(),
                        config::getDebugLightLevelRadius,
                        val -> {
                            config.setDebugLightLevelRadius(val);
                            dirty = true;
                        })
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(0, 10)
                        .step(1)
                        .formatValue(v -> v <= 0
                                ? Component.translatable("options.off").withStyle(ChatFormatting.RED)
                                : Component.literal(String.format("%d", v))))
                .build());

        debugGroup.option(createBooleanOption(
                config.getDebugDisplayHandlerBoundingBox(),
                "lambdynlights.option.debug.display_behavior_bounding_box.tooltip"));

        builder.group(debugGroup.build());

        return builder.build();
    }

    public static void applyChanges() {
        if (dirty) {
            LambDynLights.get().config.save();
            dirty = false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Option<Boolean> createBooleanOption(SettingEntry<Boolean> entry, String tooltipKey) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("lambdynlights.option." + entry.key()))
                .description(OptionDescription.of(Component.translatable(tooltipKey)))
                .binding(((SettingEntryAccessor<Boolean>) entry).getDefaultValue(), entry::get, val -> {
                    entry.set(val);
                    dirty = true;
                })
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Integer> createAdaptiveTickingOption(
            String keySuffix, Supplier<Integer> getter, Consumer<Integer> setter) {
        int defaultValue = keySuffix.equals("slow")
                ? DynamicLightsConfigAccessor.getDefaultSlowTickingDistance()
                : DynamicLightsConfigAccessor.getDefaultSlowerTickingDistance();
        return Option.<Integer>createBuilder()
                .name(Component.translatable("lambdynlights.option.adaptive_ticking." + keySuffix))
                .description(OptionDescription.of(
                        Component.translatable("lambdynlights.option.adaptive_ticking." + keySuffix + ".tooltip")))
                .binding(defaultValue, () -> (int) (Math.sqrt(getter.get()) / 16), setter)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(1, 33)
                        .step(1)
                        .formatValue(v -> v == 33
                                ? Component.translatable("options.off").withStyle(ChatFormatting.RED)
                                : Component.literal(String.valueOf(v))))
                .build();
    }
}
