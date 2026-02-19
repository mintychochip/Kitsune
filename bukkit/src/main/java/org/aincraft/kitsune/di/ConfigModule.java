package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import org.aincraft.kitsune.config.Configuration;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.config.KitsuneConfigInterface;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigModule extends AbstractModule {

    @Provides @Singleton
    Configuration provideConfiguration(JavaPlugin plugin) {
        var section = plugin.getConfig();
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getString": return section.getString((String) args[0], (String) args[1]);
                case "getInt": return section.getInt((String) args[0], (int) args[1]);
                case "getBoolean": return section.getBoolean((String) args[0], (boolean) args[1]);
                case "getDouble": return section.getDouble((String) args[0], (double) args[1]);
                case "getKeys":
                    var sub = section.getConfigurationSection((String) args[0]);
                    return sub == null ? Collections.emptySet() : sub.getKeys(false);
                case "toString": return section.toString();
                default: throw new UnsupportedOperationException("Method not supported: " + method.getName());
            }
        };
        return (Configuration) Proxy.newProxyInstance(
            Configuration.class.getClassLoader(),
            new Class<?>[] { Configuration.class },
            handler
        );
    }

    @Provides @Singleton
    KitsuneConfigInterface provideKitsuneConfig(Configuration config) {
        return new KitsuneConfig(config);
    }
}