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
                    .description(OptionDescription.of(Text.literal(
                            "Clears More Culling's internal cache. Useful if you experience visual glitches after changing settings.")))
                    .action((screen, button) -> CacheUtils.resetAllCache())
                    .build());
        }
    }
}
