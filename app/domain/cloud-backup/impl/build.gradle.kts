import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.gradle.exclude

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.kotlin.serialization)
  id("build.wallet.redacted")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.domain.emergencyExitKitPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.keyValueStorePublic)
        implementation(projects.libs.platformPublic)
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.recoveryPublic)
        implementation(projects.domain.relationshipsPublic)
        implementation(projects.libs.cloudStorePublic)
        implementation(projects.domain.authPublic)
        implementation(projects.libs.stdlibPublic)
        implementation(libs.kmp.settings)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.availabilityFake)
        implementation(projects.domain.recoveryFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.cloudBackupFake) {
          exclude(projects.domain.cloudBackupPublic)
        }
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.domain.emergencyExitKitFake)
        implementation(projects.domain.recoveryPublic)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.encryptionFake)
        implementation(projects.libs.loggingTesting)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
