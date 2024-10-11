
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.shared.databasePublic)
        implementation(projects.shared.f8eClientPublic)
        implementation(projects.shared.coroutinesPublic)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
      }
    }

    val commonJvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.encryptionImpl)
      }
    }
  }
}
