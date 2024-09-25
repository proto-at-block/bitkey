
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.shared.recoveryPublic)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.databasePublic)
        implementation(projects.shared.recoveryImpl)
      }
    }
  }
}
