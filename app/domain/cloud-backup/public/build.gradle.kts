import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.gradle.exclude

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
  id("build.wallet.redacted")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.bitcoinPrimitivesPublic)
        api(projects.domain.keyboxPublic)
        api(projects.libs.cloudStorePublic)
        api(projects.libs.encryptionPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.cloudBackupFake) {
          exclude(projects.domain.cloudBackupPublic)
        }
      }
    }
  }
}
