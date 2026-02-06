package dev.isxander.xso;

import dev.isxander.xso.mixins.OptionImplAccessor;
import dev.isxander.yacl3.api.Binding;

import net.caffeinemc.mods.sodium.client.gui.options.Option;
import net.caffeinemc.mods.sodium.client.gui.options.binding.OptionBinding;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;

public class SodiumBinding<S, T> implements Binding<T> {
    private final OptionBinding<S, T> sodiumBinding;
    private final OptionStorage<S> sodiumStorage;

    @SuppressWarnings("unchecked")
    public SodiumBinding(Option<T> sodiumOption) {
        this(((OptionImplAccessor<S, T>) sodiumOption).getBinding(), (OptionStorage<S>) sodiumOption.getStorage());
    }

    public SodiumBinding(OptionBinding<S, T> sodiumBinding, OptionStorage<S> sodiumStorage) {
        this.sodiumBinding = sodiumBinding;
        this.sodiumStorage = sodiumStorage;
    }

    @Override
    public void setValue(T value) {
        sodiumBinding.setValue(sodiumStorage.getData(), value);
    }

    @Override
    public T getValue() {
        return sodiumBinding.getValue(sodiumStorage.getData());
    }

    @Override
    @SuppressWarnings("unchecked")
    public T defaultValue() {
        try {
            S currentData = sodiumStorage.getData();
            if (currentData != null) {
                Class<?> storageClass = currentData.getClass();

                try {
                    java.lang.reflect.Method defaultsMethod = storageClass.getMethod("defaults");
                    if (storageClass.isAssignableFrom(defaultsMethod.getReturnType())) {
                        S defaultData = (S) defaultsMethod.invoke(null);
                        return sodiumBinding.getValue(defaultData);
                    }
                } catch (Exception e) {
                    /* ignore */ }

                try {
                    java.lang.reflect.Constructor<?> constructor = storageClass.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    S defaultData = (S) constructor.newInstance();
                    return sodiumBinding.getValue(defaultData);
                } catch (Exception e) {
                    /* ignore */ }

                try {
                    java.lang.reflect.Constructor<?> constructor = storageClass
                            .getDeclaredConstructor(net.minecraft.client.MinecraftClient.class, java.io.File.class);
                    constructor.setAccessible(true);

                    java.io.File tempFile = java.io.File.createTempFile("xso_defaults", ".txt");
                    tempFile.delete();

                    S defaultData = (S) constructor.newInstance(net.minecraft.client.MinecraftClient.getInstance(),
                            tempFile);
                    return sodiumBinding.getValue(defaultData);
                } catch (Exception e) {
                    /* ignore */
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return getValue();
    }
}
