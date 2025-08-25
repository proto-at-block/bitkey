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
        implementation(projects.libs.composeRuntimePublic)
        implementation(projects.libs.keyValueStorePublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kmp.molecule.runtime)
        implementation(projects.libs.testingPublic)
        implementation(projects.ui.frameworkTesting)
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.platformFake)
      }
    }
  }
}
