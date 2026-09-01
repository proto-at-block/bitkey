import org.gradle.api.initialization.resolve.RepositoriesMode

/**
 * Standalone Gradle build for Trailblaze UI tests.
 *
 * This is intentionally NOT part of the main app build (app/settings.gradle.kts). The test APK
 * drives the separately installed debug app over the accessibility APIs and depends on zero app
 * modules, while its dependency tree (trailblaze → koog → Kotlin 2.3.x, ktor 3.x) is
 * incompatible with the main build's verifiable-builds dependency locking. See README.md in
 * this directory before attempting to merge it into the main build.
 */
rootProject.name = "trailblaze-tests"

pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

  repositories {
    exclusiveContent {
      forRepository {
        maven {
          // Trailblaze artifacts are VENDORED (built from source at a pinned tag) because the
          // artifacts trailblaze publishes to Maven Central snapshots are missing their Android
          // variants and don't work on-device. See vendor/update-vendored-trailblaze.sh for the
          // full story and the regeneration procedure.
          url = uri("$rootDir/vendor/maven")
        }
      }
      filter {
        includeGroup("xyz.block.trailblaze")
      }
    }

    // Public dependencies resolve from the standard repos; integrity (and dependency-confusion
    // defense) is enforced by gradle/verification-metadata.xml, not by an internal mirror. See
    // README.md ("Vendored trailblaze artifacts").
    google()
    mavenCentral()
  }
}
