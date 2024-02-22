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
        api(projects.shared.bitcoinPublic)
        api(projects.shared.emergencyAccessKitPublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.platformPublic)
        api(projects.shared.accountPublic)
        api(projects.shared.recoveryPublic)
        api(projects.shared.cloudStorePublic)
        implementation(projects.shared.coroutinesPublic)
        implementation(projects.shared.serializationPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kmp.test.kotest.assertions.json)
        implementation(projects.shared.accountFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.cloudBackupFake) {
          exclude(projects.shared.cloudBackupPublic)
        }
        implementation(projects.shared.cloudStoreFake)
        implementation(projects.shared.recoveryPublic)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.encryptionFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.keyValueStoreFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
