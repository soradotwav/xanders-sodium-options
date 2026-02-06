package dev.isxander.xso.compat;

import ca.fxco.moreculling.config.sodium.MoreCullingSodiumOptionImpl;
import ca.fxco.moreculling.utils.CacheUtils;
import dev.isxander.xso.SodiumBinding;
import dev.isxander.xso.mixins.compat.moreculling.MoreCullingSodiumOptionImplAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionDescription;
import net.caffeinemc.mods.sodium.client.gui.options.Option;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.text.Text;

public class MoreCullingCompat {

    @SuppressWarnings({"unchecked"})
    public static <S, T> SodiumBinding<S, T> getBinding(Option<T> option) {
        if (isMoreCullingOption(option)) {
            return new SodiumBinding<>(
                    ((MoreCullingSodiumOptionImplAccessor<S, T>) option).getBinding(),
                    (OptionStorage<S>) option.getStorage());
        } else {
            return new SodiumBinding<>(option);
        }
    }

    public static boolean isMoreCullingOption(Option<?> option) {
        return option instanceof MoreCullingSodiumOptionImpl<?, ?>;
    }

    @SuppressWarnings({"unchecked"})
    public static <T> void addAvailableCheck(dev.isxander.yacl3.api.Option<T> yaclOption, Option<T> sodiumOption) {
        if (!(sodiumOption instanceof MoreCullingSodiumOptionImpl<?, ?>)) return;

        ((OptionHolder<T>) sodiumOption).holdOption(yaclOption);
    }

    public static void extendMoreCullingPage(OptionPage page, ConfigCategory.Builder builder) {
        if (page.getName().getString().equals("MoreCulling")) {
            builder.option(dev.isxander.yacl3.api.ButtonOption.createBuilder()
                    .name(Text.translatable("moreculling.config.resetCache"))
                    .description(OptionDescription.of(Text.translatable("options.moreculling.resetcache.description")))
                    .text(Text.literal("➔"))
                    .action((screen, button) -> CacheUtils.resetAllCache())
                    .build());
        }
    }

    public interface OptionHolder<T> {
        void holdOption(dev.isxander.yacl3.api.Option<T> option);
    }
}
