
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.shared.bitkeyPrimitivesPublic)
        api(projects.shared.databasePublic)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.inheritanceFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.testingPublic)
        api(projects.shared.f8eClientPublic)
      }
    }
  }
}
