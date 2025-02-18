import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.composeRuntimePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.testingPublic)
        implementation(libs.kmp.molecule.runtime)
        implementation(projects.shared.uiCoreTesting)
      }
    }
  }
}
