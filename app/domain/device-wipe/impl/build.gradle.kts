import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.domain.deviceWipePublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.domain.featureFlagPublic)
        implementation(projects.domain.hardwarePublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.domain.recoveryPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.deviceWipeFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.hardwareFake)
        implementation(projects.domain.recoveryFake)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.bitcoinPrimitivesFake)
        implementation(projects.libs.testingPublic)
        implementation(libs.kmp.test.kotest.assertions)
      }
    }
  }
}
