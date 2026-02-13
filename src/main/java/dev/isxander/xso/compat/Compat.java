package dev.isxander.xso.compat;

import dev.isxander.xso.utils.CategoryDescriptions;

//? fabric {
/*
import net.fabricmc.loader.api.FabricLoader;
 */
//?} elif neoforge {
import net.neoforged.fml.ModList;
//?}

public enum Compat {
    SODIUM_EXTRA("sodium-extra", "xso.category.sodium_extra"),
    MORE_CULLING("moreculling", "xso.category.moreculling"),
    IRIS("iris", "xso.category.iris"),

    LAMBDYNAMICLIGHTS(

            //? fabric {
            /*
            "lambdynlights",
            */
            //?} elif neoforge {
            "lambdynlights_runtime",
            //?}
            "xso.category.lambdynlights"
    ),

    XSO("xanders_sodium_options", "xso.category.xso"),
    ENTITY_VIEW_DISTANCE("entity-view-distance", "xso.category.entity_view_distance");

    public final String modId;
    public final String descriptionKey;
    public final boolean isLoaded;

    Compat(String modId, String descriptionKey) {
        this.modId = modId;
        this.descriptionKey = descriptionKey;
        this.isLoaded = mod(modId);
    }

    public void registerCategory(String categoryName) {
        CategoryDescriptions.registerCategoryModId(categoryName, this.modId);
    }

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

