package dev.cohesion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.cohesion.CohesionConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ServerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("Cohesion");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private List<ModConfigEntry> mods = new ArrayList<>();

    public List<ModConfigEntry> getMods() {
        return mods;
    }

    public static class ModConfigEntry {
        private String slug = "";
        private String versionId = "";
        private boolean required = true;

        public String getSlug() { return slug; }
        public String getVersionId() { return versionId; }
        public boolean isRequired() { return required; }
    }

    public static ServerConfig load(Path serverDir) {
        Path configDir = serverDir.resolve("config");
        Path configFile = configDir.resolve(CohesionConstants.SERVER_CONFIG_FILENAME);

        if (!Files.exists(configFile)) {
            LOGGER.info("No {} found, creating default config.", CohesionConstants.SERVER_CONFIG_FILENAME);
            ServerConfig defaultConfig = new ServerConfig();
            save(defaultConfig, configFile);
            return defaultConfig;
        }

        try {
            String json = Files.readString(configFile);
            ServerConfig config = GSON.fromJson(json, ServerConfig.class);
            if (config == null) config = new ServerConfig();
            if (config.mods == null) config.mods = new ArrayList<>();
            return config;
        } catch (IOException e) {
            LOGGER.error("Failed to read config file: {}", configFile, e);
            return new ServerConfig();
        }
    }

    private static void save(ServerConfig config, Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(config));
        } catch (IOException e) {
            LOGGER.error("Failed to write default config file: {}", configFile, e);
        }
    }
}
