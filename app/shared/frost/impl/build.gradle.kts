import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    val commonJvmMain by getting {
      dependencies {
        implementation(projects.rust.coreFfi)
      }
    }
  }
}