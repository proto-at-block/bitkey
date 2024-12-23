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
        implementation(projects.shared.databasePublic)
        implementation(projects.shared.resultPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.partnershipsFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.timeFake)
        implementation(projects.shared.testingPublic)
      }
    }

    val commonIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
