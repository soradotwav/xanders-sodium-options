package dev.isxander.xso;

import dev.isxander.xso.config.XsoConfig;

//? fabric {

import net.fabricmc.api.ClientModInitializer;

public class ModEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        XsoConfig.load();
    }
}
 
//?} elif neoforge {
/*import net.neoforged.fml.common.Mod;

@Mod("xanders_sodium_options")
public class ModEntrypoint {

    public ModEntrypoint() {
        XsoConfig.load();
    }
}
*///?}
