# Module Structure Context for Bitkey Mobile App

This document provides essential context for AI tools working with the Bitkey mobile app's modular architecture. This context is optimized for AI tooling and does not need to be perfect for human consumption. Understanding this structure is critical for making correct decisions about where to place new code, how to structure dependencies, and how to maintain clean separation of concerns.

---

## 1. Module Structure Philosophy

The Bitkey mobile app follows **interface-first development** with a strict separation between APIs and implementations. This approach:

- Promotes separation of concerns and dependency inversion
- Prevents circular dependencies
- Improves build times through incremental compilation and caching
- Enables cleaner testing setups
- Forces adherence to modern design practices

---

## 2. Module Types and Rules

### `:public` Modules
**Purpose**: Contains minimal, high-level API interfaces and protocols.

**Rules**:
- Can only depend on other `:public` modules
- Any modules can depend on `:public` modules
- Must not leak implementation details
- Should contain concise, minimal APIs

### `:impl` Modules  
**Purpose**: Contains concrete implementations of APIs defined in sibling `:public` modules.

**Rules**:
- Transitively pulls in sibling `:public` module
- Can only depend on `:public` modules (never other `:impl` modules)
- Only top-level application modules can depend on `:impl` modules
- Contains DI setup to bind implementations (especially on Android)

### `:fake` Modules
**Purpose**: Contains fake implementations and mocks of public APIs for testing purposes.

**Rules**:
- Implements APIs from sibling `:public` modules
- Only tests are allowed to depend on `:fake` modules
- Used across the codebase for testing
- Contains both fakes and mocks

### `:testing` Modules
**Purpose**: Contains testing utilities and shared test infrastructure.

**Rules**:
- Very rare and usually not recommended
- Valid but should be used sparingly
- Contains utilities that are shared across multiple test modules

---

## 3. Top-Level Directory Structure

| Directory | Description | Purpose |
|-----------|-------------|---------|
| `android` | Android app | Platform-specific Android application code |
| `ios` | iOS app | Platform-specific iOS application code |
| `rust` | Rust bindings | Bindings for Rust "Core" library used by apps |
| `gradle` | Build logic | Gradle build logic plugins and version catalogs |
| `domain` | Domain logic | Domain-specific business services and logic |
| `libs` | Infrastructure | Domain-agnostic infrastructure libraries |
| `ui` | Presentation | UI features, state machines, presenters, design system |
| `shared` | Legacy KMP | Contains KMP shared modules (transitional) |

### Important Notes:
- **`shared` directory**: Contains legacy KMP shared modules that will eventually be broken down and moved to `domain`, `libs`, or other appropriate locations as needed
- **`settings.gradle.kts`**: The ultimate source of truth for all modules indexed and compiled by the project

---

## 4. Dependency Flow Rules

**Key Principles**:
1. **Only app modules and DI graph modules** can depend on `:impl` modules
2. **`:impl` modules** can only depend on `:public` modules
3. **`:public` modules** can depend on other `:public` modules
4. **Tests** can depend on `:fake` modules for testing
5. **No `:impl` to `:impl`** dependencies allowed

### Current Reality and Exceptions
While `:impl` to `:impl` dependencies are not allowed, there are some valid and invalid exceptions currently in the codebase:

**Valid exceptions**:
- Final app modules that pull in all dependencies are allowed to depend on `:impl` modules
- DI graph modules need to depend on `:impl` modules to wire up the dependency graph

**Invalid exceptions** (technical debt to be cleaned up):
- Some tests or other modules currently depend on `:impl` modules
- These dependencies are bad for incremental build times
- Ideally these would be cleaned up over time

---

## 5. Historical Context and Current Challenges

### Background
- The interface-first approach was adopted to promote clean architecture
- Has successfully prevented circular dependencies and maintained build performance
- Lacks comprehensive documentation and naming guidelines

### Current Issues Being Addressed
- **Documentation Gap**: Missing guidelines for module structuring
- **Unclear Boundaries**: Modules lack clear definitions, leading to implementation leakage
- **Build Performance**: Monolithic modules (e.g., `:state-machine`) are becoming build bottlenecks
- **Testing Friction**: Current structure makes organized testing more difficult

### Current Enforcement
- **No build-time checks**: Module dependency rules are enforced through convention and code review, not automated tooling
- **Future consideration**: Build-time enforcement could be added but is not currently implemented

---

## 6. Module Granularity Strategy

**Philosophy**: Create as few modules as possible while maintaining clean boundaries.

### Trade-offs to Consider:
- **More, smaller modules**:
  - Improve incremental build times
  - Slow down IDE indexing
  - Slower clean builds
  
- **Fewer, larger modules**:
  - Faster IDE indexing
  - Faster clean builds  
  - Slower incremental builds

**Guidance**: Balance module count carefully. Only create new modules when there's a clear architectural boundary that justifies the overhead.

---

## 7. AI Tool Guidelines

When working with this codebase:

### For New Features
1. **Start with `:public`**: Define clean, minimal interfaces first
2. **Create `:impl`**: Implement concrete functionality in separate module
3. **Add `:fake`**: Create test doubles for the public API
4. **Wire in App**: Only bind implementations in top-level app modules

### For Existing Code
1. **Check `settings.gradle.kts`**: Verify module structure and dependencies
2. **Respect boundaries**: Never create `:impl` to `:impl` dependencies
3. **Consider migration**: Legacy code in `shared` may need restructuring
4. **Follow naming**: Use consistent `:public`/`:impl`/`:fake` suffixes

### For Dependencies
1. **Public APIs only**: Most modules should only depend on `:public` modules
2. **App-level wiring**: Dependency injection happens at the app level
3. **Test isolation**: Use `:fake` modules for testing, not real implementations

---

## 8. Module Discovery

To understand the current module structure:
- **Primary source**: `settings.gradle.kts` contains all active modules
- **Directory exploration**: Each top-level directory contains related modules
- **Naming convention**: Look for `:public`, `:impl`, `:fake` suffixes
- **Legacy consideration**: Some modules in `shared` may not follow current conventions

This modular architecture is designed to scale efficiently while maintaining clean separation of concerns and fast build times. AI tools should always respect these boundaries when suggesting code changes or new implementations.
