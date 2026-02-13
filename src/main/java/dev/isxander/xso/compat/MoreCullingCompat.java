package dev.isxander.xso.compat;

import ca.fxco.moreculling.utils.CacheUtils;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public class MoreCullingCompat {

    public static void addResetCacheButton(OptionGroup.Builder groupBuilder) {
        groupBuilder.option(ButtonOption.createBuilder()
                .name(Component.translatable("moreculling.config.resetCache"))
                .text(Component.literal("➔"))
                .description(OptionDescription.of(Component.translatable("options.moreculling.resetcache.description")))
                .action((screen, button) -> CacheUtils.resetAllCache())
                .build());
    }
}
