package dev.cohesion.client;

import dev.cohesion.CohesionConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModFileManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Cohesion");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String DISABLED_SUFFIX = ".disabled";

    private final Path gameDir;
    private final Path cacheDir;
    private final Path modsDir;

    public ModFileManager(Path gameDir) {
        this.gameDir = gameDir;
        this.cacheDir = gameDir.resolve(CohesionConstants.CACHE_DIR_NAME).resolve(CohesionConstants.CACHE_SUBDIR);
        this.modsDir = gameDir.resolve("mods");
    }

    public Path getCacheDir() { return cacheDir; }
    public Path getModsDir() { return modsDir; }

    /**
     * Clean up .disabled files from previous syncs.
     * Called at client startup when files are no longer locked.
     */
    public void cleanupDisabledMods() {
        try {
            if (!Files.isDirectory(modsDir)) return;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*" + DISABLED_SUFFIX)) {
                for (Path disabled : stream) {
                    Files.deleteIfExists(disabled);
                    LOGGER.info("Cleaned up disabled mod: {}", disabled.getFileName());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to clean up disabled mods", e);
        }
    }

    public boolean isInCache(String sha512) {
        return Files.exists(cacheDir.resolve(sha512 + ".jar"));
    }

    public CompletableFuture<Path> downloadToCache(String downloadUrl, String expectedSha512) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(cacheDir);
                Path targetFile = cacheDir.resolve(expectedSha512 + ".jar");

                if (Files.exists(targetFile)) {
                    LOGGER.debug("File already in cache: {}", expectedSha512);
                    return targetFile;
                }

                LOGGER.info("Downloading from: {}", downloadUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("User-Agent", CohesionConstants.USER_AGENT)
                        .timeout(Duration.ofSeconds(120))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    throw new IOException("Download failed with status " + response.statusCode());
                }

                // Download to a temp file first, then verify and move
                Path tempFile = cacheDir.resolve(expectedSha512 + ".jar.tmp");
                try (InputStream in = response.body()) {
                    Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }

                // Verify SHA-512
                String actualHash = computeSha512(tempFile);
                if (!actualHash.equals(expectedSha512)) {
                    Files.deleteIfExists(tempFile);
                    throw new SecurityException("SHA-512 mismatch for download. Expected: "
                            + expectedSha512 + ", Got: " + actualHash);
                }

                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Downloaded and verified: {}", targetFile.getFileName());
                return targetFile;
            } catch (Exception e) {
                throw new RuntimeException("Failed to download mod: " + downloadUrl, e);
            }
        });
    }

    private static final List<String> PROTECTED_PREFIXES = List.of(
            "cohesion", "fabric-api", "fabric-loader"
    );

    public static boolean isProtected(String filename) {
        String lower = filename.toLowerCase();
        return PROTECTED_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    public void copyFromCacheToMods(String sha512, String targetFilename) throws IOException {
        if (isProtected(targetFilename)) {
            LOGGER.warn("Refusing to overwrite protected mod: {}", targetFilename);
            return;
        }
        Path source = cacheDir.resolve(sha512 + ".jar");
        Path target = modsDir.resolve(targetFilename);
        Files.createDirectories(modsDir);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info("Installed mod: {}", targetFilename);
    }

    public void deleteFromMods(String filename) throws IOException {
        if (isProtected(filename)) {
            LOGGER.warn("Refusing to delete protected mod: {}", filename);
            return;
        }
        Path target = modsDir.resolve(filename);
        if (Files.exists(target)) {
            try {
                Files.delete(target);
                LOGGER.info("Removed managed mod: {}", filename);
            } catch (IOException e) {
                // File is locked (Windows) — rename to .disabled so Fabric ignores it
                Path disabled = modsDir.resolve(filename + DISABLED_SUFFIX);
                Files.move(target, disabled, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Disabled managed mod (locked): {} -> {}", filename, disabled.getFileName());
            }
        }
    }

    public static String computeSha512(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] fileBytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(fileBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-512 not available", e);
        }
    }
}
