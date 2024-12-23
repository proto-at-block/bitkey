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
        implementation(projects.shared.composeRuntimePublic)
        api(projects.shared.accountPublic)
        api(projects.shared.authPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.encryptionPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.recoveryPublic)
        implementation(projects.shared.onboardingPublic)
        implementation(projects.shared.relationshipsPublic)
        implementation(libs.kmp.settings)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kmp.molecule.runtime)
        implementation(projects.shared.accountFake)
        implementation(projects.shared.authFake) {
          exclude(projects.shared.authPublic)
        }
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.encryptionFake)
        implementation(projects.shared.keyValueStoreFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.availabilityImpl)
        implementation(projects.shared.cloudBackupFake)
      }
    }
  }
}
