package dev.isxander.xso.compat;

import dev.isxander.xso.XandersSodiumOptions;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.PlaceholderCategory;
import dev.lambdaurora.lambdynlights.gui.SettingsScreen;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class LDLCompat {
    public static ConfigCategory createLdcCategory(Screen prevScreen, VideoSettingsScreen videoSettingsScreen) {
        return PlaceholderCategory.createBuilder()
                .name(Text.translatable("lambdynlights"))
                .screen((client, screen) -> {
                    try {
                        Screen proxy = new Screen(Text.empty()) {
                            @Override
                            protected void init() {
                                client.setScreen(XandersSodiumOptions.wrapSodiumScreen(
                                        videoSettingsScreen, ConfigManager.CONFIG.getModOptions(), prevScreen));
                            }
                        };
                        return new SettingsScreen(proxy);
                    } catch (Exception e) {
                        XandersSodiumOptions.LOGGER.error("Failed to open LambDynamicLights settings screen", e);

                        return new net.minecraft.client.gui.screen.NoticeScreen(
                                () -> client.setScreen(null),
                                Text.literal("LambDynamicLights Integration Error"),
                                Text.literal(
                                        "Xander's Sodium Options failed to open LambDynamicLights settings screen.\n\n"
                                                + e.getMessage()));
                    }
                })
                .build();
    }
}
