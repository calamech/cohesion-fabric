package dev.cohesion.client;

import dev.cohesion.CohesionConstants;
import dev.cohesion.client.network.ClientPacketHandler;
import dev.cohesion.network.ModManifestS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class CohesionClientMod implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Cohesion");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Cohesion client initializing...");

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();

        // Safety net: if the janitor task file still exists, the janitor didn't
        // finish (player restarted too fast). Apply tasks now — files are unlocked.
        applyLeftoverTasks(gameDir);

        // Clear pending restart marker (mods are now loaded after restart)
        ModSyncManager.clearPendingRestart(gameDir);

        // Clean up .disabled mods from previous sync sessions
        new ModFileManager(gameDir).cleanupDisabledMods();

        ClientConfigurationNetworking.registerGlobalReceiver(
                ModManifestS2CPayload.TYPE,
                (payload, context) -> {
                    LOGGER.info("Received mod manifest with {} entries.", payload.entries().size());
                    ClientPacketHandler.handleManifest(payload.entries(), context);
                }
        );

        LOGGER.info("Cohesion client initialized.");
    }

    /**
     * If the janitor task file still exists at startup, the janitor either didn't run
     * or the player restarted before it finished. Apply the tasks now since
     * files are no longer locked by the previous JVM.
     */
    private static void applyLeftoverTasks(Path gameDir) {
        Path taskFile = gameDir.resolve(CohesionConstants.CACHE_DIR_NAME)
                .resolve(ModSyncManager.TASK_FILENAME);

        if (!Files.exists(taskFile)) return;

        LOGGER.info("Found leftover janitor tasks, applying now...");

        try {
            List<String> lines = Files.readAllLines(taskFile);
            int success = 0;
            int errors = 0;

            for (String line : lines) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|", 3);
                try {
                    switch (parts[0]) {
                        case "delete" -> {
                            Path target = Path.of(parts[1]);
                            Files.deleteIfExists(target);
                            LOGGER.info("Deleted leftover: {}", target.getFileName());
                        }
                        case "copy" -> {
                            Path target = Path.of(parts[1]);
                            Path source = Path.of(parts[2]);
                            Files.createDirectories(target.getParent());
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                            LOGGER.info("Copied leftover: {} -> {}", source.getFileName(), target.getFileName());
                        }
                    }
                    success++;
                } catch (Exception e) {
                    LOGGER.warn("Failed to apply leftover task '{}': {}", line, e.getMessage());
                    errors++;
                }
            }

            Files.deleteIfExists(taskFile);
            LOGGER.info("Applied {} leftover task(s), {} error(s).", success, errors);
        } catch (Exception e) {
            LOGGER.error("Failed to process leftover janitor tasks", e);
        }
    }
}
