# Commit Conventions

## Format
```
<type>(<scope>): <description>
```

## Types
- `feat` — New feature or functionality
- `fix` — Bug fix
- `refactor` — Code restructuring without behavior change
- `docs` — Documentation only
- `chore` — Build, CI, dependencies, tooling
- `style` — Formatting, whitespace, no code change
- `test` — Adding or updating tests

## Scopes
- `server` — Server-side code (`CohesionMod`, `ServerModResolver`, `ServerConfig`)
- `client` — Client-side code (`CohesionClientMod`, `ModSyncManager`, `ModFileManager`, `ModRegistry`)
- `network` — Networking packets and tasks (`ModManifestS2CPayload`, `ManifestAckC2SPayload`, `ModSyncConfigurationTask`)
- `ui` — UI screens (`SyncScreen`, `SyncCompleteScreen`)
- `janitor` — Janitor process (`CohesionJanitor`)
- `config` — Configuration (`ServerConfig`, `ModEntry`)
- `build` — Gradle, dependencies, build scripts

Scope is optional for broad changes.

## Rules
- Description in lowercase, no period at the end
- Keep the first line under 72 characters
- Use imperative mood ("add feature" not "added feature")
- One commit per logical change
- Always sign with: `Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>`

## Examples
```
feat(client): add startup safety net for leftover janitor tasks
fix(janitor): reduce post-exit delay from 2s to 500ms
refactor(network): switch to StreamCodec API
docs: update readme with janitor architecture
chore(build): bump fabric-loom to 1.10
```
