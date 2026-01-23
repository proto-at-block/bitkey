---
name: run-gradle
description: "Build, compile, run gradle tasks, fix daemon issues, resolve build failures. Multi-agent: queue builds via agent-task-queue MCP to prevent daemon proliferation. Use when: 'gradle build', 'compile', 'build failed', 'daemon hanging', 'gradle stuck', 'run task', 'module path', 'clean build'"
---

# Gradle Build

## Multi-Agent Builds

When multiple AI agents run in parallel, use the **agent-task-queue MCP** to serialize Gradle commands and prevent daemon proliferation:

```
mcp__agent-task-queue__run_task(
  command="gradle :module:compileKotlin --console=plain",
  working_directory="/path/to/project",
  queue_name="gradle"
)
```

This queues builds and runs them sequentially, avoiding lock contention.

## Commands

```bash
gradle <task>                    # Use gradle, never ./gradlew
gradle -q <task>                 # Quiet (default)
gradle --console=plain <task>    # Parseable output (recommended)
gradle --warn <task>             # Diagnostic context
```

## Daemon Recovery

When builds hang or behave unexpectedly:

```bash
gradle --stop                    # Graceful stop
pkill -9 -f "GradleDaemon"       # Force kill if hung
pkill -9 -f "kotlin-daemon"      # Kill Kotlin daemons too
```

## Module Paths

Hyphenated for `:public`, `:impl`, `:testing`, `:fake`:

```kotlin
implementation(projects.domain.walletImpl)   // correct
implementation(projects.domain.wallet.impl)  // incorrect
```

## Common Tasks

```bash
gradle projects                  # List modules
gradle tasks                     # List tasks
gradle compileKotlin             # Compile all
gradle clean                     # Clean build
gradle :module:compileKotlin     # Single module
```

## Build Cache

Build output at `_build/` (not `build/`)

## Module List

See `settings.gradle.kts` for full module structure.

@docs/docs/mobile/architecture/gradle-modules.md
