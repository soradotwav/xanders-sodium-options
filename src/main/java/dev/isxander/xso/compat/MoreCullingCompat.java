package dev.isxander.xso.compat;

import ca.fxco.moreculling.utils.CacheUtils;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionDescription;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.minecraft.text.Text;

public class MoreCullingCompat {

    public static void extendMoreCullingPage(OptionPage page, ConfigCategory.Builder builder) {
        if (page.name().equals(Text.translatable("moreculling.config.category.general"))) {
            builder.option(ButtonOption.createBuilder()
                    .name(Text.translatable("moreculling.config.resetCache"))
                    .text(Text.literal("➔"))
                    .description(
                            OptionDescription.of(
                                    Text.translatable("options.moreculling.resetcache.description"
                                            )))
                    .action((screen, button) -> CacheUtils.resetAllCache())
                    .build());
        }
    }
}
