package dev.isxander.xso.compat;

//? fabric {
/*
import net.fabricmc.loader.api.FabricLoader;
 */
//?} elif neoforge {
import net.neoforged.fml.ModList;
//?}

public class Compat {
    public static final boolean MORE_CULLING = mod("moreculling");
    public static final boolean IRIS = mod("iris");
    public static final boolean LAMBDYNAMICLIGHTS = mod("lambdynlights");

    private static boolean mod(String id) {
        //? fabric {
        /*
        return FabricLoader.getInstance().isModLoaded(id);
         */
        //?} elif neoforge {
        return ModList.get().isLoaded(id);
        //?}
    }
}
