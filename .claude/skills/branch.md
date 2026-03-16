# Branch Naming Conventions

## Format
```
<type>/<short-description>
```

## Types
- `feat` — New feature or functionality
- `fix` — Bug fix
- `refactor` — Code restructuring without behavior change
- `docs` — Documentation only
- `chore` — Build, CI, dependencies, tooling
- `style` — Formatting, whitespace, no code change
- `test` — Adding or updating tests

## Rules
- Description in lowercase, kebab-case
- Keep it short and descriptive (under 40 characters total)
- Use imperative mood ("add-cache" not "adding-cache")
- No scope in branch names — keep it flat
- Branch off from the appropriate MC version branch (see CLAUDE.md for branching strategy)

## Examples
```
feat/mod-cache
fix/windows-file-lock
refactor/packet-registration
docs/add-changelogs
chore/bump-fabric-loom
```
