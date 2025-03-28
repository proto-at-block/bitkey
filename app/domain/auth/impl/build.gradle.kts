import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.gradle.exclude

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(compose.runtime)
        implementation(projects.libs.composeRuntimePublic)
        api(projects.domain.accountPublic)
        api(projects.domain.authPublic)
        api(projects.domain.databasePublic)
        api(projects.libs.encryptionPublic)
        api(projects.libs.keyValueStorePublic)
        api(projects.domain.recoveryPublic)
        api(projects.domain.walletPublic)
        implementation(projects.domain.onboardingPublic)
        implementation(projects.domain.relationshipsPublic)
        implementation(libs.kmp.settings)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kmp.molecule.runtime)
        implementation(projects.domain.accountFake)
        implementation(projects.domain.authFake) {
          exclude(projects.domain.authPublic)
        }
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.libs.encryptionFake)
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.domain.recoveryFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.domain.availabilityImpl)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.domain.walletFake)
      }
    }
  }
}
