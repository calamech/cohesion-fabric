# Cohesion - Fabric Mod for Automatic Mod Sync via Modrinth

## Project Overview
Fabric mod that syncs mods between server and client using the Modrinth API. Server defines required mods in `config/cohesion_server.json` with Modrinth version IDs. Client downloads/removes mods automatically during the Configuration networking phase.

## Tech Stack
- Fabric Loader, Fabric API, Java 21+
- Mojang mappings (not Yarn)
- Gradle with fabric-loom
- No external deps beyond what MC bundles (Gson, SLF4J)

## Key Architecture Decisions

### Networking Phase Ordering
Cohesion registers in `BEFORE_CONFIGURE` with an **early phase** (`cohesion:early`) ordered before `Event.DEFAULT_PHASE`. This ensures the mod sync task runs BEFORE Fabric's registry sync (`fabric-registry-sync-v0`), which also registers in `BEFORE_CONFIGURE` at default phase. Without this, clients switching servers get "unknown registry entries" errors.

### Janitor Process (File Locking on Windows)
Windows locks loaded .jar files, preventing deletion/modification while MC runs. Solution:
- `ModSyncManager.applySync()` writes a line-based task file (`pending_tasks.txt`) with `delete|path` and `copy|target|source` entries
- `ModSyncManager.spawnJanitor()` spawns `java -cp <mod-jar> dev.cohesion.client.CohesionJanitor <pid> <taskFile>` — finds the mod JAR via `FabricLoader.getModContainer().getOrigin().getPaths()`
- `CohesionJanitor` has **zero external dependencies** (no Gson) so it runs outside Fabric's classloader
- **Startup safety net**: `CohesionClientMod.applyLeftoverTasks()` applies any remaining tasks if the player restarted before the janitor finished
- Shell scripts (.bat/.sh) are NOT acceptable — antivirus may block them

### Protected Mods
Files starting with `cohesion`, `fabric-api`, or `fabric-loader` are never modified/deleted. Checked at multiple levels: server resolution, client diff, file operations, registry save.

### Server Mod Resolution
`ServerModResolver` resolves Modrinth version IDs at server startup via batch API (`/v2/versions?ids=[...]`), then recursively resolves dependencies filtered by game version + fabric loader. Results cached in memory.

## File Structure
```
src/main/java/dev/cohesion/
  CohesionMod.java              — Server ModInitializer
  CohesionConstants.java        — MOD_ID, packet IDs, paths
  config/
    ServerConfig.java           — JSON config loader
    ModEntry.java               — Record: slug, versionId, sha512, filename, downloadUrl, required
  network/
    ModManifestS2CPayload.java  — S2C packet (CustomPacketPayload + StreamCodec)
    ManifestAckC2SPayload.java  — C2S ack packet
    ModSyncConfigurationTask.java — ConfigurationTask blocking config phase
  server/
    ServerModResolver.java      — Modrinth API resolution + dependency resolution
  client/
    CohesionClientMod.java      — Client initializer + startup cleanup
    CohesionJanitor.java        — Standalone janitor process (zero deps)
    ModSyncManager.java         — Diff engine + sync orchestration
    ModRegistry.java            — managed_mods.json persistence
    ModFileManager.java         — File ops, cache, download with SHA-512 verification
    network/
      ClientPacketHandler.java  — Manifest routing
    ui/
      SyncScreen.java           — Sync confirmation UI
      SyncCompleteScreen.java   — Post-sync "Close Game" screen
```

## Test Servers (Docker)
- `servers/fabric-1.21.2/` — Port 25567, mod: waystones
- Use `itzg/minecraft-server` image with `MODRINTH_PROJECTS` multiline format
- **Always use `ONLINE_MODE: "TRUE"`** — test servers must run in online mode
- **Always use server+client mods** for testing (not client-only mods like Mod Menu)

## Important Notes
- `CustomPacketPayload` + `StreamCodec` API (not the older `FabricPacket` API)
- `PayloadTypeRegistry.configurationS2C()` / `configurationC2S()` for packet registration
