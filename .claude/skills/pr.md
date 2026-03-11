# Pull Request Conventions

## Branch Naming
```
<type>/<short-description>
```
Examples: `feat/janitor-process`, `fix/registry-sync-ordering`, `refactor/remove-shell-scripts`

## PR Title
Same format as commits:
```
<type>(<scope>): <description>
```
Keep under 70 characters.

## PR Body Format
```markdown
## Summary
<1-3 bullet points describing what changed and why>

## Changes
<List of notable file changes, grouped logically>

## Test plan
- [ ] `./gradlew build` passes
- [ ] <manual test steps relevant to the change>
```

## Rules
- PR should target `main` branch
- One PR per feature/fix — keep PRs focused
- Squash merge preferred for clean history
- Always push before creating the PR
- Include test plan with concrete verification steps
