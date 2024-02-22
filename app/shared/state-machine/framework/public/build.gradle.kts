import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.kotlin.coroutines.native)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        implementation(libs.kmp.molecule.runtime)
        implementation(projects.shared.composeRuntimePublic)
        implementation(projects.shared.loggingPublic)
      }
    }
  }
}
