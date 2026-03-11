package dev.cohesion.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Standalone process spawned by Cohesion before Minecraft shuts down.
 * Waits for the parent MC process to exit, then performs file operations
 * (delete old mods, copy new mods from cache) when files are no longer locked.
 *
 * IMPORTANT: This class must have ZERO external dependencies (no Gson, no Fabric, etc.)
 * because it is run via {@code java -cp <mod-jar>} outside of Fabric's classloader.
 *
 * Task file format (one task per line):
 *   delete|/absolute/path/to/mod.jar
 *   copy|/absolute/path/to/target.jar|/absolute/path/to/source.jar
 *
 * Usage: java -cp <cohesion-mod.jar> dev.cohesion.client.CohesionJanitor <parentPid> <taskFilePath>
 */
public class CohesionJanitor {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: CohesionJanitor <parentPid> <taskFilePath>");
            System.exit(1);
        }

        long parentPid = Long.parseLong(args[0]);
        Path taskFile = Path.of(args[1]);

        System.out.println("[Cohesion Janitor] Waiting for Minecraft (PID " + parentPid + ") to exit...");

        // Wait for the parent process to exit
        ProcessHandle.of(parentPid).ifPresent(ph -> {
            while (ph.isAlive()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        // Small extra delay to ensure file locks are released
        Thread.sleep(500);

        System.out.println("[Cohesion Janitor] Minecraft exited. Applying mod changes...");

        if (!Files.exists(taskFile)) {
            System.err.println("[Cohesion Janitor] Task file not found: " + taskFile);
            System.exit(1);
        }

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
                        if (Files.exists(target)) {
                            Files.delete(target);
                            System.out.println("[Cohesion Janitor] Deleted: " + target.getFileName());
                        }
                    }
                    case "copy" -> {
                        Path target = Path.of(parts[1]);
                        Path source = Path.of(parts[2]);
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("[Cohesion Janitor] Copied: " + source.getFileName() + " -> " + target.getFileName());
                    }
                    default -> {
                        System.err.println("[Cohesion Janitor] Unknown action: " + parts[0]);
                        errors++;
                        continue;
                    }
                }
                success++;
            } catch (IOException e) {
                System.err.println("[Cohesion Janitor] Error processing '" + line + "': " + e.getMessage());
                errors++;
            }
        }

        // Clean up the task file
        Files.deleteIfExists(taskFile);

        System.out.println("[Cohesion Janitor] Done. " + success + " operations succeeded, " + errors + " errors.");
    }
}
