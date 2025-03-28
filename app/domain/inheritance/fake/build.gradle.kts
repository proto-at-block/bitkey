
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.domain.debugPublic)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
