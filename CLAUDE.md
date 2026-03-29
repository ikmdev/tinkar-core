# Tinkar Core

Core data model, entity types, providers, and reasoning services for the Tinkar architecture.

## Build Standards

Files in `.claude/standards/` are build artifacts unpacked from `ike-build-standards`. DO NOT edit or commit them. See the workspace root CLAUDE.md for details.

## Build

```bash
mvn clean verify -DskipTests -T4
```

## Key Facts

- GroupId: `dev.ikm.tinkar`
- Uses `--enable-preview` (Java 25) — set via `maven.compiler.enablePreview` in properties
- BOM: imports `dev.ikm.ike:ike-bom` for dependency version management
- Sub-aggregators (provider, reasoner, language-extensions) use `<subprojects>`
- `tinkar-bom` submodule manages internal dependency versions for consumers
