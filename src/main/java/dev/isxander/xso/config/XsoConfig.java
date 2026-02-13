package dev.isxander.xso.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

//? fabric {

import net.fabricmc.loader.api.FabricLoader;
 
//?} elif neoforge {
/*import net.neoforged.fml.loading.FMLPaths;
*///?}

public class XsoConfig {
    private static boolean dirty = false;

    public static final ConfigClassHandler<XsoConfig> INSTANCE = ConfigClassHandler.createBuilder(XsoConfig.class)
            .id(Identifier.fromNamespaceAndPath("xso", "config"))
            .serializer(handler -> GsonConfigSerializerBuilder.create(handler)
                    //? fabric {
                    
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("xanders-sodium-options.json"))
                     
                    //?} elif neoforge {
                    /*.setPath(FMLPaths.CONFIGDIR.get().resolve("xanders-sodium-options.json"))
                    *///?}
                    .appendGsonBuilder(com.google.gson.GsonBuilder::setPrettyPrinting)
                    .build())
            .build();

    @SerialEntry
    public boolean enabled = true;

    @SerialEntry
    public boolean lenientOptions = true;

    @SerialEntry
    public boolean hardCrash = false;

    @SerialEntry
    public boolean externalMenus = false;

    public static ConfigCategory getConfigCategory() {
        XsoConfig config = INSTANCE.instance();
        XsoConfig defaults = INSTANCE.defaults();

        return ConfigCategory.createBuilder()
                .name(Component.translatable("xso.title"))
                .option(LabelOption.create(Component.empty()))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("xso.cfg.enabled"))
                        .description(OptionDescription.of(Component.translatable("xso.cfg.enabled.tooltip")))
                        .binding(defaults.enabled, () -> config.enabled, val -> {
                            config.enabled = val;
                            dirty = true;
                        })
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("xso.cfg.lenient_opts"))
                        .description(OptionDescription.of(Component.translatable("xso.cfg.lenient_opts.tooltip")))
                        .binding(defaults.lenientOptions, () -> config.lenientOptions, val -> {
                            config.lenientOptions = val;
                            dirty = true;
                        })
                        .controller(opt -> BooleanControllerBuilder.create(opt)
                                .yesNoFormatter()
                                .coloured(false))
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("xso.cfg.hard_crash"))
                        .description(OptionDescription.of(Component.translatable("xso.cfg.hard_crash.tooltip")))
                        .binding(defaults.hardCrash, () -> config.hardCrash, val -> {
                            config.hardCrash = val;
                            dirty = true;
                        })
                        .controller(opt -> BooleanControllerBuilder.create(opt)
                                .yesNoFormatter()
                                .coloured(false))
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("xso.cfg.external_menus"))
                        .description(OptionDescription.of(Component.translatable("xso.cfg.external_menus.tooltip")))
                        .binding(defaults.externalMenus, () -> config.externalMenus, val -> {
                            config.externalMenus = val;
                            dirty = true;
                        })
                        .controller(opt -> BooleanControllerBuilder.create(opt)
                                .yesNoFormatter()
                                .coloured(false))
                        .build())
                .build();
    }

    public static void load() {
        INSTANCE.load();
    }

    public static void applyChanges() {
        if (dirty) {
            INSTANCE.save();
            dirty = false;
        }
    }
}
