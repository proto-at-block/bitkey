import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(android = true, jvm = true)

  sourceSets {
    val androidMain by getting {
      dependencies {
        api(compose.runtime)
        api(libs.android.google.auth)
        api(libs.kmp.kotlin.result)
      }
    }
  }
}
