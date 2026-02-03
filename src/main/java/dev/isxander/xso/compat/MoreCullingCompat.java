package dev.isxander.xso.compat;

import ca.fxco.moreculling.utils.CacheUtils;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.minecraft.text.Text;

public class MoreCullingCompat {

    public static void extendMoreCullingPage(OptionPage page, ConfigCategory.Builder builder) {
        if (page.name()
                .getString()
                .equals(Text.translatable("moreculling.title").getString())) {
            builder.option(ButtonOption.createBuilder()
                    .name(Text.translatable("moreculling.config.resetCache"))
                    .action((screen, button) -> CacheUtils.resetAllCache())
                    .build());
        }
    }
}
