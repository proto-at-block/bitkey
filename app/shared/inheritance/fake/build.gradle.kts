
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.shared.debugPublic)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.testingPublic)
        api(projects.shared.bitcoinFake)
      }
    }
  }
}
