package dev.isxander.xso;

import dev.isxander.yacl3.api.Binding;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;

public class SodiumBinding<T> implements Binding<T> {
    private final StatefulOption<T> sodiumOption;

    public SodiumBinding(StatefulOption<T> sodiumOption) {
        this.sodiumOption = sodiumOption;
    }

    @Override
    public void setValue(T value) {
        sodiumOption.modifyValue(value);
    }

    @Override
    public T getValue() {
        return sodiumOption.getValidatedValue();
    }

    @Override
    public T defaultValue() {
        return sodiumOption.getDefaultValue().get(ConfigManager.CONFIG);
    }
}
