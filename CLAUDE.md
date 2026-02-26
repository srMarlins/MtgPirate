# CLAUDE.md

## Workflow Requirements

### Git Worktrees
All feature work MUST use worktrees — never work directly on `main`.

```bash
git worktree add ../MtgPirate-<feature-name> -b feature/<feature-name>
git worktree remove ../MtgPirate-<feature-name>  # after merge
```

### GitHub Issues First
Create a GitHub issue BEFORE starting any work.

Tag simple/mechanical tasks with the `jules` label (dependency bumps, unused import removal, formatting, basic tests, lint fixes, doc updates). Do NOT tag architectural changes, business logic, multi-file refactors, or anything requiring judgment.

### Pull Requests
Every change goes through a PR with a summary and test plan. Link to the issue with `Closes #<number>`. Prefer small, focused PRs.

### Testing
Always verify changes before opening a PR:

```bash
./gradlew build           # Compile check
./gradlew detekt          # Static analysis
./gradlew allTests        # Unit tests
./gradlew run             # Launch desktop app and manually test the workflow
```

### Parallel Subagents
Use subagents with worktrees for independent work streams. When multiple issues can be worked on simultaneously, dispatch parallel subagent worktrees.

## Conventions

- Branch naming: `feature/<name>`, `fix/<name>`, `refactor/<name>`
- Commit messages: concise, describe the "why"
- No wildcard imports
- Named constants for magic numbers and colors (in Theme.kt)
- Composable functions use PascalCase
- All dependency versions live in `gradle/libs.versions.toml`
- Platform-specific code uses expect/actual pattern
