package dev.isxander.xso.compat;

import net.fabricmc.loader.api.FabricLoader;

public class Compat {
    public static final boolean SODIUM_EXTRA = mod("sodium-extra");
    public static final boolean MORE_CULLING = mod("moreculling");
    public static final boolean IRIS = mod("iris");

    private static boolean mod(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }
}
