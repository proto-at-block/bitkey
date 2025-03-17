import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(libs.kmp.kotlin.inject.runtime)
        api(libs.kmp.kotlin.inject.anvil.runtime)
      }
    }
  }
}
