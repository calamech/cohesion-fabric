package dev.cohesion.client;

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
import java.util.Set;
import java.util.stream.Collectors;

public class ModRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("Cohesion");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String lastServerIp = "";
    private List<ManagedMod> managedMods = new ArrayList<>();

    public static class ManagedMod {
        private String filename = "";
        private String sha512 = "";
        private String versionId = "";

        public ManagedMod() {}

        public ManagedMod(String filename, String sha512, String versionId) {
            this.filename = filename;
            this.sha512 = sha512;
            this.versionId = versionId;
        }

        public String getFilename() { return filename; }
        public String getSha512() { return sha512; }
        public String getVersionId() { return versionId; }
    }

    public String getLastServerIp() { return lastServerIp; }
    public void setLastServerIp(String ip) { this.lastServerIp = ip; }

    public List<ManagedMod> getManagedMods() { return managedMods; }
    public void setManagedMods(List<ManagedMod> mods) { this.managedMods = new ArrayList<>(mods); }

    public Set<String> getManagedFilenames() {
        return managedMods.stream()
                .map(ManagedMod::getFilename)
                .collect(Collectors.toSet());
    }

    public boolean isManaged(String filename) {
        return managedMods.stream().anyMatch(m -> m.getFilename().equals(filename));
    }

    public static ModRegistry load(Path gameDir) {
        Path registryFile = gameDir.resolve(CohesionConstants.CACHE_DIR_NAME)
                .resolve(CohesionConstants.REGISTRY_FILENAME);

        if (!Files.exists(registryFile)) {
            return new ModRegistry();
        }

        try {
            String json = Files.readString(registryFile);
            ModRegistry registry = GSON.fromJson(json, ModRegistry.class);
            if (registry == null) return new ModRegistry();
            if (registry.managedMods == null) registry.managedMods = new ArrayList<>();
            return registry;
        } catch (IOException e) {
            LOGGER.error("Failed to load mod registry", e);
            return new ModRegistry();
        }
    }

    public void save(Path gameDir) {
        Path registryFile = gameDir.resolve(CohesionConstants.CACHE_DIR_NAME)
                .resolve(CohesionConstants.REGISTRY_FILENAME);
        try {
            Files.createDirectories(registryFile.getParent());
            Files.writeString(registryFile, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.error("Failed to save mod registry", e);
        }
    }
}
