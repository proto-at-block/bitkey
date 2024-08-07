@file:Suppress("UnstableApiUsage")

package build.wallet.gradle.logic

import build.wallet.gradle.logic.gradle.addDependency
import build.wallet.gradle.logic.gradle.libs
import com.android.build.api.dsl.ApplicationBaseFlavor
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Common extension properties for the Android Application. Library and Dynamic Feature Plugins.
 * Only the Android Gradle Plugin should create instances of this interface.
 */
internal typealias AndroidExtension = CommonExtension<*, *, *, *, *, *>

/**
 * Applies common configuration to an Android module (app or library).
 *
 * [AndroidExtension] is either a [ApplicationExtension] (for app module), or [LibraryExtension]
 * (for library module).
 *
 * This is meant to be used for internal build logic only.
 */
internal fun AndroidExtension.commonConfiguration(project: Project) {
  require(this is ApplicationExtension || this is LibraryExtension) {
    "Only Android library or app modules are supported, receiver is of type ${this::class}."
  }

  configureJvmTarget(project)
  configureAndroidSdk(project)
  configureJavaDesugaring(project)
  configureAndroidPackagingOptions()
  configureStubsInUniTests()
  configureInstrumentationTestsRunner()
}

private fun AndroidExtension.configureAndroidSdk(project: Project) {
  buildToolsVersion = project.libs.versions.android.build.tools
    .get()
  compileSdk = project.libs.versions.android.sdk.compile
    .get()
    .toInt()

  val targetSdkVersion = project.libs.versions.android.sdk.target
    .get()
    .toInt()
  defaultConfig {
    minSdk = project.libs.versions.android.sdk.min
      .get()
      .toInt()
    if (this is ApplicationBaseFlavor) {
      targetSdk = targetSdkVersion
    } else {
      testOptions {
        // TODO(W-4027): enable back when on AGP 8.2.0
        // targetSdk = targetSdkVersion
      }
    }
  }
}

/**
 * Configure build process to exclude various files from final app/library artifact.
 *
 * This is primarily done to avoid conflict issues when packaging artifacts.
 * We have multiple library dependencies which include various files with colliding names.
 * We don't care to include these files in our app so we exclude them from getting packaged.
 */
private fun AndroidExtension.configureAndroidPackagingOptions() {
  packaging {
    resources {
      // For Google libraries.
      excludes += "META-INF/DEPENDENCIES"

      // For preventing kotlinx-coroutines-debug library from packaging incorrect resources.
      excludes += "**/attach_hotspot_windows.dll"
      excludes += "META-INF/AL2.0"
      excludes += "META-INF/LGPL2.1"
      excludes += "META-INF/licenses/ASM"
    }
  }
}

/**
 * Adds support for Java 8 APIs on older Android APIs.
 * See https://developer.android.com/studio/write/java8-support#library-desugaring.
 */
private fun AndroidExtension.configureJavaDesugaring(project: Project) {
  compileOptions {
    isCoreLibraryDesugaringEnabled = true

    project.dependencies {
      addDependency("coreLibraryDesugaring", project.libs.android.tools.desugarJdkLibs)
    }
  }
}

/**
 * Configure to return default values when mockable Android APIs are called instead of throwing.
 *
 * https://developer.android.com/training/testing/local-tests#mocking-dependencies
 */
private fun AndroidExtension.configureStubsInUniTests() =
  testOptions {
    unitTests.isReturnDefaultValues = true
  }

/**
 * Configures the Android default test instrumentation runner.
 */
private fun AndroidExtension.configureInstrumentationTestsRunner() {
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
}

/**
 * Configures the Android project's Java source and bytecode compatibility to match the specified
 * JVM target version.
 */
private fun AndroidExtension.configureJvmTarget(project: Project) =
  compileOptions {
    val jvmTarget = JavaVersion.toVersion(
      project.libs.versions.jvmTarget
        .get()
    )
    sourceCompatibility = jvmTarget
    targetCompatibility = jvmTarget
  }
