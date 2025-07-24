# Kotlin Multiplatform (KMP) Context for Bitkey Mobile App

This file provides system-level context for AI tools working on the Bitkey mobile codebase. It defines what is shared via Kotlin Multiplatform (KMP), what is not, and how the source sets are structured. All AI agents must reference this document before generating or modifying shared or platform-specific code.

---

## 1. Shared Code via KMP

The majority of Bitkey mobile app logic is shared using Kotlin Multiplatform.

Shared components include:

- Domain business logic (e.g., state machines)
- UI Presentation models
- UI components and screens via **Compose UI Multiplatform**
- Data access layer via **DAOs**
- Domain models
- SQLDelight-based database layer, which generates KMP-compatible code

All shared logic resides in `commonMain` and associated source sets.

---

## 2. Platform-Specific Code (Not Shared)

Some code is not shared via KMP due to platform constraints or unsupported dependencies. This includes:

- Native platform APIs
  - Cryptography, NFC, phone number parsing
- Dependencies not supported in KMP
  - Example: BDK artifacts

**Note:**  
Platform-specific code is implemented using **KMP features such as `expect`/`actual` classes and functions** wherever applicable. This allows shared business logic to depend on platform-agnostic contracts, with platform-specific implementations provided per target.

Additionally, platform-specific code is **structured to align with shared abstractions and architecture patterns**, improving consistency and enabling future migration to shared modules where feasible.

---

## 3. KMP Targets

We use the following Kotlin targets:

- `android` → Android app
- `ios` → iOS app
- `jvm` → for compiling and running shared code and tests

---

## 4. Compilation Units

The codebase defines the following compilation sets:

- `main`: production code (shared and platform-specific)
- `test`: unit tests (depends on `main`)
- `integrationTest`: integration tests (depends on `main`)

---

## 5. Source Sets

To manage cross-platform logic, we define the following source sets:

- `commonMain`: common shared logic
- `commonJvmMain`: JVM-specific shared logic (shared between Android and JVM targets)
- `androidMain`, `iosMain`, `jvmMain`: platform-specific logic
- `commonTest`, `commonJvmTest`, `androidUnitTest`, `iosTest`, `jvmTest`: platform-specific unit tests
- `commonIntegrationTest`, `commonJvmIntegrationTest`, `jvmIntegrationTest`: platform-specific integration tests
