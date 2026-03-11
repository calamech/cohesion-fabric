package dev.cohesion.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cohesion.CohesionConstants;
import dev.cohesion.config.ModEntry;
import dev.cohesion.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ServerModResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("Cohesion");
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Protected mod slugs/project IDs that should never be synced
    private static final Set<String> PROTECTED_SLUGS = Set.of(
            "fabric-api", "fabric-language-kotlin", "cohesion"
    );

    private static List<ModEntry> resolvedMods = List.of();
    private static String serverMcVersion = "";

    public static List<ModEntry> getResolvedMods() {
        return resolvedMods;
    }

    public static void setServerMcVersion(String version) {
        serverMcVersion = version;
    }

    public static CompletableFuture<Void> resolve(ServerConfig config) {
        List<ServerConfig.ModConfigEntry> configEntries = config.getMods();
        if (configEntries.isEmpty()) {
            LOGGER.info("No mods configured for sync.");
            resolvedMods = List.of();
            return CompletableFuture.completedFuture(null);
        }

        // Build initial version IDs to fetch
        Map<String, ServerConfig.ModConfigEntry> configMap = new HashMap<>();
        List<String> versionIds = new ArrayList<>();
        for (ServerConfig.ModConfigEntry entry : configEntries) {
            configMap.put(entry.getVersionId(), entry);
            versionIds.add(entry.getVersionId());
        }

        return fetchAndResolveDependencies(versionIds, configMap).thenAccept(resolved -> {
            resolvedMods = List.copyOf(resolved);
            LOGGER.info("Resolved {} mod(s) total for sync (including dependencies).", resolved.size());
        }).exceptionally(ex -> {
            LOGGER.error("Failed to resolve mods from Modrinth API", ex);
            resolvedMods = List.of();
            return null;
        });
    }

    private static CompletableFuture<List<ModEntry>> fetchAndResolveDependencies(
            List<String> versionIds, Map<String, ServerConfig.ModConfigEntry> configMap) {

        // Track all resolved version IDs to avoid duplicates
        Set<String> resolvedVersionIds = new HashSet<>();
        List<ModEntry> allResolved = new ArrayList<>();

        return resolveRecursive(versionIds, configMap, resolvedVersionIds, allResolved, 0);
    }

    private static CompletableFuture<List<ModEntry>> resolveRecursive(
            List<String> versionIds,
            Map<String, ServerConfig.ModConfigEntry> configMap,
            Set<String> resolvedVersionIds,
            List<ModEntry> allResolved,
            int depth) {

        if (depth > 5) {
            LOGGER.warn("Dependency resolution depth limit reached (5), stopping.");
            return CompletableFuture.completedFuture(allResolved);
        }

        // Filter out already resolved version IDs
        List<String> toFetch = versionIds.stream()
                .filter(id -> !resolvedVersionIds.contains(id))
                .toList();

        if (toFetch.isEmpty()) {
            return CompletableFuture.completedFuture(allResolved);
        }

        return fetchVersions(toFetch).thenCompose(versions -> {
            List<String> dependencyVersionIds = new ArrayList<>();
            List<String> dependencyProjectIds = new ArrayList<>();

            for (JsonElement element : versions) {
                JsonObject version = element.getAsJsonObject();
                String versionId = version.get("id").getAsString();

                if (resolvedVersionIds.contains(versionId)) continue;
                resolvedVersionIds.add(versionId);

                // Extract project slug from the version
                String projectId = version.has("project_id") ? version.get("project_id").getAsString() : "";

                // Check if this is a protected mod
                String slug = "";
                if (configMap.containsKey(versionId)) {
                    slug = configMap.get(versionId).getSlug();
                }
                // For dependencies (not in configMap), we'll use project_id as slug
                if (slug.isEmpty()) {
                    slug = projectId;
                }

                if (isProtectedProject(slug)) {
                    LOGGER.debug("Skipping protected mod: {}", slug);
                    continue;
                }

                // Find the primary file
                JsonArray files = version.getAsJsonArray("files");
                JsonObject primaryFile = findPrimaryFile(files);
                if (primaryFile == null) {
                    LOGGER.warn("No files found for version {}", versionId);
                    continue;
                }

                String filename = primaryFile.get("filename").getAsString();
                String downloadUrl = primaryFile.get("url").getAsString();
                JsonObject hashes = primaryFile.getAsJsonObject("hashes");
                String sha512 = hashes.get("sha512").getAsString();

                // Determine if required: explicit config entry or inherited from parent
                boolean required = configMap.containsKey(versionId)
                        ? configMap.get(versionId).isRequired()
                        : true; // dependencies are required by default

                ModEntry modEntry = new ModEntry(slug, versionId, sha512, filename, downloadUrl, required);
                allResolved.add(modEntry);

                boolean isDependency = !configMap.containsKey(versionId);
                LOGGER.info("Resolved {}: {} ({}) -> {}",
                        isDependency ? "dependency" : "mod", slug, versionId, filename);

                // Parse dependencies
                if (version.has("dependencies") && version.get("dependencies").isJsonArray()) {
                    for (JsonElement depEl : version.getAsJsonArray("dependencies")) {
                        JsonObject dep = depEl.getAsJsonObject();
                        String depType = dep.has("dependency_type") ? dep.get("dependency_type").getAsString() : "";

                        if (!"required".equals(depType)) continue;

                        // Dependency can have version_id (exact) or project_id (needs resolution)
                        if (dep.has("version_id") && !dep.get("version_id").isJsonNull()) {
                            String depVersionId = dep.get("version_id").getAsString();
                            if (!resolvedVersionIds.contains(depVersionId)) {
                                dependencyVersionIds.add(depVersionId);
                            }
                        } else if (dep.has("project_id") && !dep.get("project_id").isJsonNull()) {
                            String depProjectId = dep.get("project_id").getAsString();
                            if (!isProtectedProject(depProjectId)) {
                                dependencyProjectIds.add(depProjectId);
                            }
                        }
                    }
                }
            }

            // Resolve project_id dependencies (find the right version for our MC version)
            CompletableFuture<Void> projectResolution;
            if (!dependencyProjectIds.isEmpty()) {
                projectResolution = resolveProjectDependencies(dependencyProjectIds, resolvedVersionIds)
                        .thenAccept(dependencyVersionIds::addAll);
            } else {
                projectResolution = CompletableFuture.completedFuture(null);
            }

            return projectResolution.thenCompose(v ->
                    resolveRecursive(dependencyVersionIds, configMap, resolvedVersionIds, allResolved, depth + 1)
            );
        });
    }

    /**
     * For dependencies that only specify a project_id, find the latest compatible version
     * for the server's Minecraft version.
     */
    private static CompletableFuture<List<String>> resolveProjectDependencies(
            List<String> projectIds, Set<String> alreadyResolved) {

        List<CompletableFuture<Optional<String>>> futures = projectIds.stream()
                .map(projectId -> resolveLatestVersion(projectId, alreadyResolved))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList());
    }

    /**
     * Query Modrinth to find the latest version of a project compatible with our MC version.
     * Uses: GET /v2/project/{id}/version?game_versions=["1.21.8"]&loaders=["fabric"]
     */
    private static CompletableFuture<Optional<String>> resolveLatestVersion(
            String projectId, Set<String> alreadyResolved) {

        String gameVersions = URLEncoder.encode("[\"" + serverMcVersion + "\"]", StandardCharsets.UTF_8);
        String loaders = URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
        String url = CohesionConstants.MODRINTH_API_BASE + "/project/" + projectId
                + "/version?game_versions=" + gameVersions + "&loaders=" + loaders;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", CohesionConstants.USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.warn("Failed to resolve project {}: HTTP {}", projectId, response.statusCode());
                        return Optional.<String>empty();
                    }
                    JsonArray versions = GSON.fromJson(response.body(), JsonArray.class);
                    if (versions == null || versions.isEmpty()) {
                        LOGGER.warn("No compatible version found for project {} (MC {})", projectId, serverMcVersion);
                        return Optional.<String>empty();
                    }
                    // First result is the latest compatible version
                    String versionId = versions.get(0).getAsJsonObject().get("id").getAsString();
                    if (alreadyResolved.contains(versionId)) {
                        return Optional.<String>empty();
                    }
                    LOGGER.info("Resolved project {} -> version {}", projectId, versionId);
                    return Optional.of(versionId);
                })
                .exceptionally(ex -> {
                    LOGGER.warn("Error resolving project {}: {}", projectId, ex.getMessage());
                    return Optional.empty();
                });
    }

    private static boolean isProtectedProject(String slugOrProjectId) {
        if (slugOrProjectId == null || slugOrProjectId.isEmpty()) return false;
        String lower = slugOrProjectId.toLowerCase();
        return PROTECTED_SLUGS.stream().anyMatch(lower::contains);
    }

    private static CompletableFuture<JsonArray> fetchVersions(List<String> versionIds) {
        StringBuilder idsParam = new StringBuilder("[");
        for (int i = 0; i < versionIds.size(); i++) {
            if (i > 0) idsParam.append(",");
            idsParam.append("\"").append(versionIds.get(i)).append("\"");
        }
        idsParam.append("]");

        String encodedIds = URLEncoder.encode(idsParam.toString(), StandardCharsets.UTF_8);
        String url = CohesionConstants.MODRINTH_API_BASE + "/versions?ids=" + encodedIds;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", CohesionConstants.USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Modrinth API returned status " + response.statusCode()
                                + ": " + response.body());
                    }
                    return GSON.fromJson(response.body(), JsonArray.class);
                });
    }

    private static JsonObject findPrimaryFile(JsonArray files) {
        if (files == null || files.isEmpty()) return null;
        for (JsonElement file : files) {
            JsonObject fileObj = file.getAsJsonObject();
            if (fileObj.has("primary") && fileObj.get("primary").getAsBoolean()) {
                return fileObj;
            }
        }
        return files.get(0).getAsJsonObject();
    }
}
