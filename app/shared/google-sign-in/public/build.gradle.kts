import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
}

kotlin {
  targets(android = true, jvm = true)

  sourceSets {
    val androidMain by getting {
      dependencies {
        api(projects.shared.resultPublic)
        api(compose.runtime)
        api(libs.android.google.auth)
      }
    }
  }
}
