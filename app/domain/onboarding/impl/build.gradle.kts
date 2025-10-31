import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.libs.keyValueStorePublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.domain.debugPublic)
        implementation(projects.domain.notificationsPublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.libs.frostPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
        implementation(libs.kmp.settings)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.authFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.onboardingFake)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
