import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.bitkeyPrimitivesPublic)
        api(projects.shared.f8ePublic)
      }
    }
  }
}
