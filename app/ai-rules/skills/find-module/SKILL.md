---
name: find-module
description: Find where to place new code. Module placement, :public/:impl/:fake, directory structure, domain/, libs/, ui/
---

# Find Module

## Before Placing Code

**Search for related modules first:**
- Search for modules in the same domain area
- Look at how similar features are organized
- Check `settings.gradle.kts` for the full module tree

When uncertain about placement, search for similar components and follow existing patterns.

## Code Placement by Type

**Kotlin Multiplatform code (most features):**
- Location: `app/` directory (domain/, libs/, ui/ subdirectories)
- Source sets: commonMain for shared, androidMain/iosMain for platform-specific
- See module index in docs for domain-specific placement

**iOS-specific code (Swift/ObjC):**
- Location: `app/ios/` directory
- Only for Swift extensions, bridging code, or iOS-only features
- Most iOS code should be in KMP modules under app/

**Rust code (FFI bindings):**
- Location: `app/rust/` directory
- core-ffi: Core crypto operations
- bdk-ffi: Bitcoin Development Kit bindings
- firmware-ffi: Hardware wallet commands (NFC, firmware interactions)
- Uses UniFFI for Kotlin/Swift bindings

**Android-specific code:**
- Location: `app/android/` directory
- Only for Android platform code (Activities, Android-only features)
- Most Android code should be in KMP modules under app/

## Steps

1. Check module index for existing modules in the domain
2. Determine module type (:public, :impl, :fake)
3. Place code in appropriate source set (prefer commonMain)

## References

@docs/docs/mobile/architecture/gradle-modules.md
@docs/docs/mobile/architecture/kmp.md
