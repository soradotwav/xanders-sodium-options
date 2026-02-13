package dev.isxander.xso.compat;

import ca.fxco.moreculling.utils.CacheUtils;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.text.Text;

public class MoreCullingCompat {

    public static void addResetCacheButton(OptionGroup.Builder groupBuilder) {
        groupBuilder.option(ButtonOption.createBuilder()
                .name(Text.translatable("moreculling.config.resetCache"))
                .text(Text.literal("➔"))
                .description(OptionDescription.of(Text.translatable("options.moreculling.resetcache.description")))
                .action((screen, button) -> CacheUtils.resetAllCache())
                .build());
    }
}
