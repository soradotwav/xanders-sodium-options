package dev.isxander.xso;

import dev.isxander.xso.mixins.OptionImplAccessor;
import dev.isxander.yacl3.api.Binding;

import net.caffeinemc.mods.sodium.client.gui.options.Option;
import net.caffeinemc.mods.sodium.client.gui.options.binding.OptionBinding;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SodiumBinding<S, T> implements Binding<T> {
    private static final Map<Class<?>, Object> DEFAULT_DATA_CACHE = new ConcurrentHashMap<>();

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

                S defaultData = (S) DEFAULT_DATA_CACHE.computeIfAbsent(storageClass,
                        SodiumBinding::createDefaultInstance);
                if (defaultData != null) {
                    return sodiumBinding.getValue(defaultData);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return getValue();
    }

    @SuppressWarnings("unchecked")
    private static <S> S createDefaultInstance(Class<?> storageClass) {
        try {
            java.lang.reflect.Method defaultsMethod = storageClass.getMethod("defaults");
            if (storageClass.isAssignableFrom(defaultsMethod.getReturnType())) {
                return (S) defaultsMethod.invoke(null);
            }
        } catch (Exception e) {
            /* ignore */
        }

        try {
            java.lang.reflect.Constructor<?> constructor = storageClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (S) constructor.newInstance();
        } catch (Exception e) {
            /* ignore */
        }

        try {
            java.lang.reflect.Constructor<?> constructor = storageClass
                    .getDeclaredConstructor(net.minecraft.client.MinecraftClient.class, java.io.File.class);
            constructor.setAccessible(true);

            java.io.File tempFile = java.io.File.createTempFile("xso_defaults", ".txt");
            tempFile.delete();

            return (S) constructor.newInstance(net.minecraft.client.MinecraftClient.getInstance(), tempFile);
        } catch (Exception e) {
            /* ignore */
        }

        return null;
    }
}
