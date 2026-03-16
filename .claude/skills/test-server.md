# Test Server Setup

## Docker Image
Always use `itzg/minecraft-server` with `TYPE: FABRIC`.

## Directory Structure
```
servers/fabric-<mc_version>/
  docker-compose.yml
  mods/                    # cohesion jar (ro volume mounted to /mods)
  data/config/             # cohesion_server.json
  .gitignore               # ignore data/ except cohesion config
```

## docker-compose.yml Template
```yaml
services:
  mc:
    image: itzg/minecraft-server
    ports:
      - "<host_port>:25565"
    environment:
      EULA: "TRUE"
      TYPE: FABRIC
      VERSION: "<mc_version>"
      FABRIC_LOADER_VERSION: "<loader_version>"
      ONLINE_MODE: "TRUE"
      MODRINTH_PROJECTS: |
        fabric-api
        waystones
        balm
      MODRINTH_ALLOWED_VERSION_TYPE: release
    volumes:
      - ./data:/data
      - ./mods:/mods:ro
    tty: true
    stdin_open: true
```

## Test Mod
Use **waystones** (+ its dependency **balm**) as the test mod. It's a client+server mod available on most MC versions via Modrinth, making it ideal for testing the full sync flow.

## cohesion_server.json
Configure with the waystones Modrinth version ID matching the server's MC version:
```json
{
  "mods": [
    {
      "slug": "waystones",
      "versionId": "<modrinth_version_id>",
      "required": true
    }
  ]
}
```
Find the correct version ID via `https://api.modrinth.com/v2/project/LOpKHB2A/version?game_versions=["<mc_version>"]&loaders=["fabric"]`.

## Port Allocation
Check running containers with `docker ps` before choosing a port. Start from 25565 and increment.

## Steps
1. Build the mod: `./gradlew build`
2. Copy jar to `servers/fabric-<mc_version>/mods/`
3. Create `data/config/cohesion_server.json` with the waystones version ID
4. `docker compose up -d`
5. Check logs: `docker compose logs -f`
6. Verify cohesion resolves mods in the logs before connecting
