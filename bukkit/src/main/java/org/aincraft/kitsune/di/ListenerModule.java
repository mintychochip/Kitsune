package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import org.aincraft.kitsune.listener.*;
import org.aincraft.kitsune.util.BukkitContainerLocationResolver;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;

public class ListenerModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<Listener> listenerBinder = Multibinder.newSetBinder(binder(), Listener.class);

        listenerBinder.addBinding().to(ContainerCloseListener.class);
        listenerBinder.addBinding().to(HopperTransferListener.class);
        listenerBinder.addBinding().to(ContainerBreakListener.class);
        listenerBinder.addBinding().to(ChestPlaceListener.class);
        listenerBinder.addBinding().to(PlayerQuitListener.class);
    }

    @Provides
    Plugin providePlugin(JavaPlugin plugin) {
        return plugin;
    }

    @Provides
    BukkitContainerLocationResolver provideContainerLocationResolver() {
        return new BukkitContainerLocationResolver();
    }
}