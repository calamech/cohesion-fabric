# Cohesion

A Fabric mod that automatically synchronizes mods between server and client using the [Modrinth API](https://docs.modrinth.com/).

When a player connects, the server sends its mod manifest during the Configuration networking phase. The client compares it against locally installed mods, downloads any missing ones from Modrinth, and prompts the player to restart if changes are needed.

## Features

- **Automatic mod sync** during the connection phase (before entering the world)
- **Modrinth-only downloads** with SHA-512 integrity verification
- **Recursive dependency resolution** — server resolves transitive dependencies via the Modrinth API
- **Content-addressable cache** at `.minecraft/cohesion/cache/` (no redundant downloads)
- **Safe mod management** — only touches files it has installed (tracked in `managed_mods.json`)
- **Protected mods** — never modifies Cohesion itself, Fabric API, or Fabric Loader
- **Seamless server switching** — handles mod additions and removals when switching between servers
- **Cross-platform janitor** — a lightweight Java process applies file changes after MC exits (handles Windows file locking)
- **Startup safety net** — if the player restarts too fast, leftover tasks are applied at next launch
- **Transparent to vanilla clients** — players without Cohesion connect normally

## Requirements

- Fabric Loader
- Fabric API
- Java 21+

## Server Setup

1. Install the Cohesion mod jar on your server (in the `mods/` folder).
2. Start the server once to generate the default config.
3. Edit `config/cohesion_server.json`:

```json
{
  "mods": [
    { "slug": "sodium", "versionId": "rLBgU2jc", "required": true },
    { "slug": "lithium", "versionId": "nMhjKWVE", "required": false }
  ]
}
```

| Field       | Description                                                        |
|-------------|--------------------------------------------------------------------|
| `slug`      | Modrinth project slug (from `modrinth.com/mod/<slug>`)             |
| `versionId` | Modrinth version ID (from the version page URL or API)             |
| `required`  | Whether the client must have this mod to join                      |

4. Restart the server. On startup, Cohesion resolves each version ID via the Modrinth API to fetch filenames, SHA-512 hashes, download URLs, and transitive dependencies.

## Client Setup

1. Install the Cohesion mod jar on the client (in `.minecraft/mods/`). You also need **Fabric API** matching your Minecraft version (download from [Modrinth](https://modrinth.com/mod/fabric-api)).
2. Connect to a Cohesion-enabled server.
3. If mods need to be installed/updated/removed, a sync screen appears listing the changes.
4. Click **Sync & Restart** to download mods and prepare changes, then close and relaunch the game.
5. On next launch, mods are ready — connect again and enter the world directly.

## How It Works

1. **Server startup**: Reads `cohesion_server.json`, queries the Modrinth API to resolve version metadata and dependencies.
2. **Player connects**: During the Configuration phase (before registry sync), the server sends a `ModManifestS2C` packet if the client has Cohesion.
3. **Client diff**: Compares the manifest against local `mods/` and `managed_mods.json`.
4. **If up-to-date**: Acknowledges immediately, configuration proceeds to Play.
5. **If changes needed**: Shows a sync screen with color-coded actions (green=install, yellow=update, red=remove).
6. **Sync & Restart**: Downloads mods to cache, writes a task file, spawns a janitor process, and shows a "Close Game" screen.
7. **Janitor process**: A standalone Java process (`CohesionJanitor`) waits for MC to exit, then copies/deletes mod files. If the player restarts before the janitor finishes, leftover tasks are applied at startup.

## Architecture

```
dev.cohesion/
  CohesionMod              — Server entrypoint: packet registration, config events
  CohesionConstants        — Shared identifiers and paths
  config/
    ServerConfig            — Loads cohesion_server.json
    ModEntry                — Record: slug, versionId, sha512, filename, downloadUrl, required
  network/
    ModManifestS2CPayload   — Server-to-client manifest packet
    ManifestAckC2SPayload   — Client-to-server acknowledgment packet
    ModSyncConfigurationTask — Blocks config phase until client acks
  server/
    ServerModResolver       — Resolves Modrinth version IDs + dependencies at startup
  client/
    CohesionClientMod       — Client entrypoint: startup cleanup, manifest handler
    CohesionJanitor         — Standalone process for post-exit file operations
    ModSyncManager          — Diff computation, download orchestration, sync apply
    ModRegistry             — Persistent tracking of managed mods (managed_mods.json)
    ModFileManager          — File I/O: download, cache, copy, delete, protection
    network/
      ClientPacketHandler   — Routes manifest to ack or SyncScreen
    ui/
      SyncScreen            — Mod sync confirmation screen
      SyncCompleteScreen    — Post-sync "Close Game" screen
```

## Building

```bash
./gradlew build
```

The mod jar is output to `build/libs/`.

## License

See [LICENSE](LICENSE).
