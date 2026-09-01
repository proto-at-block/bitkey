buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
    classpath(libs.pluginClasspath.kotlin)
    classpath(libs.pluginClasspath.android)
    classpath(libs.pluginClasspath.detekt)
    classpath(libs.pluginClasspath.kmp.kotest)
    classpath(libs.pluginClasspath.android.paparazzi)
    classpath(libs.pluginClasspath.google.services)
  }
}

plugins {
  id("build.wallet.dependency-locking")
  id("build.wallet.dependency-locking.common-group-configuration")
  id("build.wallet.dependency-locking.dependency-configuration")
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.licensee) apply false
  alias(libs.plugins.compose.runtime) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.paparazzi) apply false
  alias(libs.plugins.sqldelight) apply false
  alias(libs.plugins.wire) apply false
  alias(libs.plugins.kotlinx.benchmark) apply false
  alias(libs.plugins.ksp) apply false
}

subprojects {
  configurations.configureEach {
    // androidx.core 1.16.0 pulls tracing 1.2.0 on runtime classpaths, but androidx.test
    // still requests 1.0.0 on androidTest compile classpaths. Our custom dependency locking
    // requires a single version per locking group, so force alignment across configurations.
    resolutionStrategy.force("androidx.tracing:tracing:1.2.0")

    resolutionStrategy.dependencySubstitution {
      substitute(module("bitkey:test-code-eliminator"))
        .using(project(":gradle:test-code-eliminator"))
        .because("Kotlin compiler plugins are installed via maven coordinates, so we substitute those coordinates with the local module.")

      substitute(module("io.kotest:kotest-framework-multiplatform-plugin-embeddable-compiler"))
        .using(project(":gradle:kotest-compiler-plugin"))
        .because("Kotest 6.0.0.M1 compiler plugin must be rebuilt against Kotlin 2.2 for iOS tests.")
    }
  }
}

tasks.wrapper {
  isEnabled = false
}

layout.buildDirectory = File("_build")
