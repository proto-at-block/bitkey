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
        api(projects.shared.bitcoinPrimitivesPublic)
        api(projects.shared.keyboxPublic)
        api(projects.shared.cloudStorePublic)
        api(projects.shared.encryptionPublic)
        api(projects.shared.f8ePublic)
        implementation(projects.shared.serializationPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.cloudBackupFake) {
          exclude(projects.shared.cloudBackupPublic)
        }
      }
    }
  }
}
