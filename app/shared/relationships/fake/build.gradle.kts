
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.authFake)
        api(projects.shared.bitcoinPrimitivesFake)
        api(projects.shared.timeFake)
        api(projects.shared.f8eClientFake)
      }
    }
  }
}
