---
description: Gradle build system usage for Bitkey mobile app
globs: ["**/build.gradle.kts", "**/*.gradle.kts", "settings.gradle.kts"]
alwaysApply: true
---

# Gradle Build System

## Summary

Gradle handles compilation, testing, and packaging for the Bitkey mobile app. Uses Hermit-managed Gradle instead of Gradle Wrapper to ensure consistent versioning. Build outputs to `_build/` directory.

## When to Apply

- Running any Gradle command or task
- Compiling, testing, or building the application
- Troubleshooting build issues

## How to Apply

### Required Sequence

**AI ASSISTANTS: Always follow this exact pattern:**

```bash
# Step 1: Activate Hermit (once per terminal session)
. bin/activate-hermit

# Step 2: Use gradle command (NEVER ./gradlew or gradlew)
gradle jvmTest
```

**Critical constraints:**
- NEVER use `./gradlew` or `gradlew` - causes version conflicts and build failures
- ALWAYS activate Hermit first - provides correct Gradle version
- Hermit activation persists per terminal session - no need to repeat

### Common Tasks

```bash
# Discovery
gradle tasks              # List available tasks
gradle projects           # Show module structure

# Project-wide
gradle jvmTest            # Run all tests
gradle compileKotlin      # Compile all Kotlin
gradle clean              # Clean _build/ directories

# Module-specific
gradle :<module>:<task>
gradle :domain:wallet-impl:jvmTest
gradle :ui:features-public:compileKotlin

# Multiple tasks
gradle clean jvmTest ktlintFormat

# Debugging
gradle <task> --stacktrace    # Show errors
gradle <task> --info          # Verbose output
gradle -q <task>              # Quiet mode
```

## Related Rules

- @ai-rules/hermit-environment.md (for Hermit binary management)
- @ai-rules/module-structure.md (for module task targeting)