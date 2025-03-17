
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.domain.databasePublic)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(projects.domain.bitcoinFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
      }
    }

    val commonJvmIntegrationTest by getting {
      dependencies {
        implementation(projects.domain.bitcoinFake)
        implementation(projects.libs.encryptionImpl)
      }
    }
  }
}
