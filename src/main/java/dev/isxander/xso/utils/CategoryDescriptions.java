package dev.isxander.xso.utils;

import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.gui.DescriptionWithName;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public class CategoryDescriptions {

    private static final String LABEL_OPTION_NAME = "Label Option";

    private static final Map<String, String> SODIUM_TAB_DESCRIPTIONS = Map.of(
            "General", "xso.category.general",
            "Quality", "xso.category.quality",
            "Performance", "xso.category.performance",
            "Advanced", "xso.category.advanced");

    private static final Map<String, String> MOD_DESCRIPTIONS = Map.of(
            "sodium-extra", "xso.category.sodium_extra",
            "moreculling", "xso.category.moreculling",
            "iris", "xso.category.iris",
            "lambdynlights", "xso.category.lambdynlights",
            "xanders-sodium-options", "xso.category.xso",
            "entity-view-distance", "xso.category.entity_view_distance");

    private static final java.util.Set<String> EXTERNAL_MENU_MODS = java.util.Set.of("iris", "lambdynlights");

    private static final Map<String, String> categoryModIds = new ConcurrentHashMap<>();

    public static void registerCategoryModId(String categoryName, String modId) {
        categoryModIds.put(categoryName, modId);
    }

    public static void clearRegistrations() {
        categoryModIds.clear();
    }

    public static DescriptionWithName getDefault(Component categoryName) {
        String name = categoryName.getString();

        String sodiumKey = SODIUM_TAB_DESCRIPTIONS.get(name);
        if (sodiumKey != null) {
            return DescriptionWithName.of(categoryName, OptionDescription.of(Component.translatable(sodiumKey)));
        }

        String modId = categoryModIds.get(name);
        if (modId != null) {
            return getDefaultForMod(categoryName, modId);
        }

        return DescriptionWithName.of(
                categoryName, OptionDescription.of(Component.translatable("xso.category.fallback")));
    }

    private static DescriptionWithName getDefaultForMod(Component categoryName, String modId) {
        String customKey = MOD_DESCRIPTIONS.get(modId);
        if (customKey != null) {
            net.minecraft.network.chat.MutableComponent description = Component.translatable(customKey);

            if (EXTERNAL_MENU_MODS.contains(modId)) {
                description = description
                        .append("\n\n")
                        .append(Component.translatable("xso.hint.use_external_menu")
                                .withStyle(
                                        net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
            }

            return DescriptionWithName.of(categoryName, OptionDescription.of(description));
        }

        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> {
                    String desc = container.getMetadata().getDescription();
                    if (desc != null && !desc.isBlank()) {
                        return DescriptionWithName.of(categoryName, OptionDescription.of(Component.literal(desc)));
                    }
                    return DescriptionWithName.of(
                            categoryName, OptionDescription.of(Component.translatable("xso.category.fallback")));
                })
                .orElseGet(() -> DescriptionWithName.of(
                        categoryName, OptionDescription.of(Component.translatable("xso.category.fallback"))));
    }

    public static boolean isLabelOptionSpacer(DescriptionWithName desc) {
        if (desc == null) return false;
        return LABEL_OPTION_NAME.equals(desc.name().getString());
    }

    public static boolean hasEmptyDescription(DescriptionWithName desc) {
        if (desc == null) return false;
        return desc.description().text().getString().isBlank();
    }
}
