package com.plexdev.plexkillstreaks;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.*;
import java.lang.reflect.Proxy;

public class TestRegistryAccess implements RegistryAccess {
    @Override public <T extends Keyed> Registry<T> getRegistry(Class<T> type) { return registry(type); }
    @Override public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        return registry(key == RegistryKey.SOUND_EVENT ? Sound.class : Keyed.class);
    }
    @SuppressWarnings("unchecked")
    private <T extends Keyed> Registry<T> registry(Class<?> type) {
        return (Registry<T>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Registry.class}, (proxy, method, args) -> {
            if (method.getName().equals("get")) {
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (entry, operation, values) -> {
                    if (operation.getName().equals("getKey") || operation.getName().equals("key")) return args[0];
                    if (operation.getName().equals("toString")) return args[0].toString();
                    if (operation.getName().equals("ordinal")) return 0;
                    if (operation.getName().equals("hashCode")) return args[0].hashCode();
                    if (operation.getName().equals("equals")) return entry == values[0];
                    return null;
                });
            }
            if (method.getName().equals("stream")) return java.util.stream.Stream.empty();
            if (method.getName().equals("iterator")) return java.util.Collections.emptyIterator();
            if (method.getName().equals("size")) return 0;
            return null;
        });
    }
}
