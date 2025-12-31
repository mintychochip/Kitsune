package org.aincraft.kitsune.storage;

import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.logging.ChestFindLogger;
import org.aincraft.kitsune.platform.PlatformContext;

import java.util.logging.Logger;

public class VectorStorageFactory {
    private VectorStorageFactory() {
    }

    public static VectorStorage create(KitsuneConfig config, PlatformContext platform) {
        String provider = config.getStorageProvider().toLowerCase();

        return switch (provider) {
            case "supabase" -> new SupabaseVectorStorage(platform.logger(), config);
            case "sqlite" -> {
                // SqliteVectorStorage uses java.util.logging.Logger
                // Create an adapter that delegates to ChestFindLogger
                Logger julLogger = createJulLoggerAdapter(platform.logger());
                String dbPath = platform.dataFolder().getDataFolder().resolve(config.getSqlitePath()).toString();
                yield new SqliteVectorStorage(julLogger, dbPath);
            }
            default -> {
                platform.logger().warning("Unknown storage provider: " + provider + ", using SQLite");
                Logger julLogger = createJulLoggerAdapter(platform.logger());
                String dbPath = platform.dataFolder().getDataFolder().resolve(config.getSqlitePath()).toString();
                yield new SqliteVectorStorage(julLogger, dbPath);
            }
        };
    }

    /**
     * Creates a java.util.logging.Logger adapter that delegates to ChestFindLogger.
     */
    private static Logger createJulLoggerAdapter(ChestFindLogger kitsuneLogger) {
        Logger julLogger = Logger.getLogger("ChestFind.Storage");
        julLogger.setUseParentHandlers(false);
        julLogger.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                String message = record.getMessage();
                Throwable thrown = record.getThrown();

                if (record.getLevel().intValue() >= java.util.logging.Level.SEVERE.intValue()) {
                    if (thrown != null) {
                        kitsuneLogger.log(ChestFindLogger.LogLevel.SEVERE, message, thrown);
                    } else {
                        kitsuneLogger.severe(message);
                    }
                } else if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                    if (thrown != null) {
                        kitsuneLogger.log(ChestFindLogger.LogLevel.WARNING, message, thrown);
                    } else {
                        kitsuneLogger.warning(message);
                    }
                } else {
                    kitsuneLogger.info(message);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        });
        return julLogger;
    }
}
