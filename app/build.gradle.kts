buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
    classpath(libs.pluginClasspath.gradleEnterprise)
    classpath(libs.pluginClasspath.kotlin)
    classpath(libs.pluginClasspath.android)
    classpath(libs.pluginClasspath.detekt)
    classpath(libs.pluginClasspath.kmp.kotest)
    classpath(libs.pluginClasspath.android.paparazzi)
    classpath(libs.pluginClasspath.google.services)
  }
}

plugins {
  id("build.wallet.scans")
  id("build.wallet.dependency-locking")
  id("build.wallet.dependency-locking.common-group-configuration")
  id("build.wallet.dependency-locking.dependency-configuration")
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.kotlin.coroutines.native) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.licensee) apply false
  alias(libs.plugins.compose.runtime) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.paparazzi) apply false
  alias(libs.plugins.sqldelight) apply false
  alias(libs.plugins.wire) apply false
}

tasks.wrapper {
  isEnabled = false
}

layout.buildDirectory = File("_build")
