package org.aincraft.kitsune.platform;

import org.aincraft.kitsune.KitsunePlatform;
import org.aincraft.kitsune.config.ConfigProvider;
import org.aincraft.kitsune.config.FabricConfigProvider;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fabric implementation of KitsunePlugin.
 * Provides platform abstractions for the common module.
 */
public final class FabricKitsunePlugin implements KitsunePlatform {
    private final Logger logger;
    private final Path dataFolder;
    private final FabricConfigProvider configProvider;

    public FabricKitsunePlugin(Path configDir, FabricConfigProvider configProvider) {
        this.logger = new Slf4jLoggerWrapper("Kitsune");
        this.dataFolder = configDir.resolve("kitsune");
        this.configProvider = configProvider;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public Path getDataFolder() {
        return dataFolder;
    }

    @Override
    public ConfigProvider getConfig() {
        return configProvider;
    }

    /**
     * Reload the configuration from disk.
     */
    public void reloadConfig() {
        configProvider.reload();
    }

    /**
     * Wrapper that bridges SLF4J logging to java.util.logging.Logger.
     * Required because the common module expects java.util.logging.Logger.
     */
    private static final class Slf4jLoggerWrapper extends Logger {
        private final org.slf4j.Logger slf4jLogger;

        Slf4jLoggerWrapper(String name) {
            super(name, null);
            this.slf4jLogger = org.slf4j.LoggerFactory.getLogger(name);
        }

        @Override
        public void log(Level level, String msg) {
            if (level == Level.SEVERE) {
                slf4jLogger.error(msg);
            } else if (level == Level.WARNING) {
                slf4jLogger.warn(msg);
            } else if (level == Level.INFO || level == Level.CONFIG) {
                slf4jLogger.info(msg);
            } else if (level == Level.FINE) {
                slf4jLogger.debug(msg);
            } else {
                slf4jLogger.trace(msg);
            }
        }

        @Override
        public void log(Level level, String msg, Object param1) {
            if (level == Level.SEVERE) {
                slf4jLogger.error(msg, param1);
            } else if (level == Level.WARNING) {
                slf4jLogger.warn(msg, param1);
            } else if (level == Level.INFO || level == Level.CONFIG) {
                slf4jLogger.info(msg, param1);
            } else if (level == Level.FINE) {
                slf4jLogger.debug(msg, param1);
            } else {
                slf4jLogger.trace(msg, param1);
            }
        }

        @Override
        public void log(Level level, String msg, Object[] params) {
            if (level == Level.SEVERE) {
                slf4jLogger.error(msg, params);
            } else if (level == Level.WARNING) {
                slf4jLogger.warn(msg, params);
            } else if (level == Level.INFO || level == Level.CONFIG) {
                slf4jLogger.info(msg, params);
            } else if (level == Level.FINE) {
                slf4jLogger.debug(msg, params);
            } else {
                slf4jLogger.trace(msg, params);
            }
        }

        @Override
        public void log(Level level, String msg, Throwable thrown) {
            if (level == Level.SEVERE) {
                slf4jLogger.error(msg, thrown);
            } else if (level == Level.WARNING) {
                slf4jLogger.warn(msg, thrown);
            } else if (level == Level.INFO || level == Level.CONFIG) {
                slf4jLogger.info(msg, thrown);
            } else if (level == Level.FINE) {
                slf4jLogger.debug(msg, thrown);
            } else {
                slf4jLogger.trace(msg, thrown);
            }
        }

        @Override
        public void info(String msg) {
            slf4jLogger.info(msg);
        }

        @Override
        public void warning(String msg) {
            slf4jLogger.warn(msg);
        }

        @Override
        public void severe(String msg) {
            slf4jLogger.error(msg);
        }

        @Override
        public void fine(String msg) {
            slf4jLogger.debug(msg);
        }

        @Override
        public void finer(String msg) {
            slf4jLogger.trace(msg);
        }

        @Override
        public void finest(String msg) {
            slf4jLogger.trace(msg);
        }

        @Override
        public boolean isLoggable(Level level) {
            if (level == Level.SEVERE || level == Level.WARNING) {
                return slf4jLogger.isWarnEnabled();
            } else if (level == Level.INFO || level == Level.CONFIG) {
                return slf4jLogger.isInfoEnabled();
            } else if (level == Level.FINE) {
                return slf4jLogger.isDebugEnabled();
            } else {
                return slf4jLogger.isTraceEnabled();
            }
        }
    }
}
