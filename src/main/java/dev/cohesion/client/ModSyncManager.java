package dev.cohesion.client;

import dev.cohesion.CohesionConstants;
import dev.cohesion.config.ModEntry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ModSyncManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Cohesion");
    static final String TASK_FILENAME = "pending_tasks.txt";

    public enum Action { INSTALL, UPDATE, REMOVE }

    public record SyncAction(ModEntry entry, Action action) {}

    private final List<ModEntry> serverManifest;
    private final ModRegistry registry;
    private final ModFileManager fileManager;
    private final Path gameDir;

    private List<SyncAction> syncActions = List.of();

    public ModSyncManager(List<ModEntry> serverManifest, Path gameDir) {
        this.serverManifest = serverManifest;
        this.gameDir = gameDir;
        this.registry = ModRegistry.load(gameDir);
        this.fileManager = new ModFileManager(gameDir);
        computeDiff();
    }

    public List<SyncAction> getSyncActions() {
        return syncActions;
    }

    public boolean isUpToDate() {
        return syncActions.isEmpty();
    }

    private void computeDiff() {
        List<SyncAction> actions = new ArrayList<>();
        Path modsDir = fileManager.getModsDir();

        // Build a set of currently managed filenames -> sha512
        Map<String, String> managedBySha = new HashMap<>();
        for (ModRegistry.ManagedMod managed : registry.getManagedMods()) {
            managedBySha.put(managed.getFilename(), managed.getSha512());
        }

        // Set of filenames the server wants
        Set<String> serverFilenames = serverManifest.stream()
                .map(ModEntry::filename)
                .collect(Collectors.toSet());

        // Check each server entry against local state (skip protected mods)
        for (ModEntry entry : serverManifest) {
            if (ModFileManager.isProtected(entry.filename())) {
                LOGGER.debug("Skipping protected mod in manifest: {}", entry.filename());
                continue;
            }
            Path localFile = modsDir.resolve(entry.filename());

            if (!Files.exists(localFile)) {
                // Mod is missing entirely
                actions.add(new SyncAction(entry, Action.INSTALL));
            } else {
                // File exists - check if it matches the expected hash
                String existingHash = managedBySha.get(entry.filename());
                if (existingHash == null || !existingHash.equals(entry.sha512())) {
                    // Hash mismatch or not managed by us -> treat as update
                    actions.add(new SyncAction(entry, Action.UPDATE));
                }
                // If hash matches, mod is up to date - no action needed
            }
        }

        // Check for managed mods no longer in the server manifest -> queue for removal
        for (ModRegistry.ManagedMod managed : registry.getManagedMods()) {
            if (ModFileManager.isProtected(managed.getFilename())) continue;
            if (!serverFilenames.contains(managed.getFilename())) {
                // Create a dummy entry for the removal action
                ModEntry removalEntry = new ModEntry(
                        "", managed.getVersionId(), managed.getSha512(),
                        managed.getFilename(), "", false
                );
                actions.add(new SyncAction(removalEntry, Action.REMOVE));
            }
        }

        this.syncActions = List.copyOf(actions);
        LOGGER.info("Computed sync diff: {} action(s) needed.", actions.size());
    }

    public CompletableFuture<Void> downloadAll() {
        List<CompletableFuture<Path>> downloads = syncActions.stream()
                .filter(a -> a.action() == Action.INSTALL || a.action() == Action.UPDATE)
                .filter(a -> !fileManager.isInCache(a.entry().sha512()))
                .map(a -> fileManager.downloadToCache(a.entry().downloadUrl(), a.entry().sha512()))
                .toList();

        return CompletableFuture.allOf(downloads.toArray(new CompletableFuture[0]));
    }

    /**
     * Write a task file and prepare file operations for the janitor process.
     * Files may be locked while MC runs, so a separate Java process handles
     * delete/copy after shutdown.
     */
    public void applySync() throws IOException {
        Path modsDir = fileManager.getModsDir();
        Path cacheDir = fileManager.getCacheDir();
        Path cohesionDir = gameDir.resolve(CohesionConstants.CACHE_DIR_NAME);
        Files.createDirectories(cohesionDir);

        List<String> taskLines = new ArrayList<>();

        // Delete commands first (for REMOVE and UPDATE actions)
        for (SyncAction action : syncActions) {
            if (action.action() == Action.REMOVE || action.action() == Action.UPDATE) {
                if (!ModFileManager.isProtected(action.entry().filename())) {
                    String target = modsDir.resolve(action.entry().filename()).toAbsolutePath().toString();
                    taskLines.add("delete|" + target);
                }
            }
        }

        // Copy commands (for INSTALL and UPDATE actions)
        for (SyncAction action : syncActions) {
            if (action.action() == Action.INSTALL || action.action() == Action.UPDATE) {
                if (!ModFileManager.isProtected(action.entry().filename())) {
                    String source = cacheDir.resolve(action.entry().sha512() + ".jar").toAbsolutePath().toString();
                    String target = modsDir.resolve(action.entry().filename()).toAbsolutePath().toString();
                    taskLines.add("copy|" + target + "|" + source);
                }
            }
        }

        Path taskFile = cohesionDir.resolve(TASK_FILENAME);
        Files.write(taskFile, taskLines);
        LOGGER.info("Wrote {} janitor task(s) to {}", taskLines.size(), taskFile);

        // Update registry now (JSON file, not locked)
        List<ModRegistry.ManagedMod> newManaged = serverManifest.stream()
                .filter(e -> !ModFileManager.isProtected(e.filename()))
                .map(e -> new ModRegistry.ManagedMod(e.filename(), e.sha512(), e.versionId()))
                .toList();
        registry.setManagedMods(newManaged);
        registry.save(gameDir);

        // Write pending restart marker
        Path marker = cohesionDir.resolve(CohesionConstants.PENDING_RESTART_FILENAME);
        Files.writeString(marker, "restart required");

        LOGGER.info("Sync prepared. {} mod(s) managed. Janitor will apply on exit.", newManaged.size());
    }

    /**
     * Spawn a separate Java process that will apply file changes after MC exits.
     * Uses {@code java -cp <our-mod-jar>} to run {@link CohesionJanitor} outside
     * of Fabric's classloader, avoiding classpath issues.
     */
    public static void spawnJanitor(Path gameDir) {
        try {
            Path cohesionDir = gameDir.resolve(CohesionConstants.CACHE_DIR_NAME);
            Path taskFile = cohesionDir.resolve(TASK_FILENAME);

            if (!Files.exists(taskFile)) {
                LOGGER.warn("No janitor task file found, skipping.");
                return;
            }

            // Find our mod JAR path via FabricLoader
            Path modJar = FabricLoader.getInstance()
                    .getModContainer(CohesionConstants.MOD_ID)
                    .orElseThrow(() -> new IllegalStateException("Cohesion mod container not found"))
                    .getOrigin()
                    .getPaths()
                    .getFirst();

            // Find the java binary that's running the current process
            String javaBin = ProcessHandle.current().info().command().orElse("java");

            long pid = ProcessHandle.current().pid();

            List<String> command = List.of(
                    javaBin,
                    "-cp", modJar.toAbsolutePath().toString(),
                    "dev.cohesion.client.CohesionJanitor",
                    String.valueOf(pid),
                    taskFile.toAbsolutePath().toString()
            );

            LOGGER.info("Spawning janitor process: {}", String.join(" ", command));

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            builder.directory(gameDir.toFile());
            builder.start();

            LOGGER.info("Janitor process spawned. It will apply changes after MC exits.");
        } catch (Exception e) {
            LOGGER.error("Failed to spawn janitor process.", e);
        }
    }

    public static boolean isPendingRestart(Path gameDir) {
        return Files.exists(gameDir.resolve(CohesionConstants.CACHE_DIR_NAME)
                .resolve(CohesionConstants.PENDING_RESTART_FILENAME));
    }

    public static void clearPendingRestart(Path gameDir) {
        try {
            Files.deleteIfExists(gameDir.resolve(CohesionConstants.CACHE_DIR_NAME)
                    .resolve(CohesionConstants.PENDING_RESTART_FILENAME));
        } catch (IOException e) {
            LOGGER.warn("Failed to clear pending restart marker", e);
        }
    }
}
