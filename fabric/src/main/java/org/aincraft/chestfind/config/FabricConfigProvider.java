package org.aincraft.chestfind.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON-based configuration provider for Fabric.
 * Reads configuration from a JSON file with dot-notation path access.
 */
public final class FabricConfigProvider implements ConfigProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestFind");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;
    private JsonObject config;

    public FabricConfigProvider(Path configDir) {
        this.configPath = configDir.resolve("chestfind").resolve("config.json");
        loadConfig();
    }

    private void loadConfig() {
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                config = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException e) {
                LOGGER.error("Failed to load config from {}", configPath, e);
                config = createDefaultConfig();
            }
        } else {
            config = createDefaultConfig();
            saveConfig();
        }
    }

    private JsonObject createDefaultConfig() {
        JsonObject root = new JsonObject();

        // Embedding settings
        JsonObject embedding = new JsonObject();
        embedding.addProperty("provider", "onnx");
        embedding.addProperty("model", "all-MiniLM-L6-v2");

        JsonObject openai = new JsonObject();
        openai.addProperty("api-key", "");
        openai.addProperty("model", "text-embedding-3-small");
        embedding.add("openai", openai);

        JsonObject google = new JsonObject();
        google.addProperty("api-key", "");
        google.addProperty("model", "text-embedding-004");
        embedding.add("google", google);

        root.add("embedding", embedding);

        // Storage settings
        JsonObject storage = new JsonObject();
        storage.addProperty("provider", "jvector");

        JsonObject supabase = new JsonObject();
        supabase.addProperty("url", "");
        supabase.addProperty("key", "");
        storage.add("supabase", supabase);

        root.add("storage", storage);

        // Indexing settings
        JsonObject indexing = new JsonObject();
        indexing.addProperty("debounce-ms", 500);
        indexing.addProperty("hopper-transfers", true);
        root.add("indexing", indexing);

        // Search settings
        JsonObject search = new JsonObject();
        search.addProperty("limit", 10);
        root.add("search", search);

        // Client settings
        JsonObject client = new JsonObject();
        client.addProperty("highlight-duration-seconds", 10);
        client.addProperty("highlight-color", "#FFD700");
        root.add("client", client);

        return root;
    }

    private void saveConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config to {}", configPath, e);
        }
    }

    /**
     * Reload configuration from disk.
     */
    public void reload() {
        loadConfig();
    }

    /**
     * Navigate to a nested JSON element using dot notation.
     * e.g., "embedding.openai.api-key"
     */
    private JsonElement navigate(String path) {
        String[] parts = path.split("\\.");
        JsonElement current = config;

        for (String part : parts) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(part);
        }

        return current;
    }

    @Override
    public String getString(String path, String defaultValue) {
        JsonElement element = navigate(path);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return defaultValue;
    }

    @Override
    public int getInt(String path, int defaultValue) {
        JsonElement element = navigate(path);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        return defaultValue;
    }

    @Override
    public boolean getBoolean(String path, boolean defaultValue) {
        JsonElement element = navigate(path);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        return defaultValue;
    }

    @Override
    public double getDouble(String path, double defaultValue) {
        JsonElement element = navigate(path);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        }
        return defaultValue;
    }
}
