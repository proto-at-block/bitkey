
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.authFake)
        api(projects.libs.bitcoinPrimitivesFake)
        api(projects.libs.timeFake)
        api(projects.domain.f8eClientFake)
      }
    }
  }
}
