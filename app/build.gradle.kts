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
    resolutionStrategy.dependencySubstitution {
      substitute(module("bitkey:test-code-eliminator"))
        .using(project(":gradle:test-code-eliminator"))
        .because("Kotlin compiler plugins are installed via maven coordinates, so we substitute those coordinates with the local module.")
    }
  }
}

tasks.wrapper {
  isEnabled = false
}

layout.buildDirectory = File("_build")
