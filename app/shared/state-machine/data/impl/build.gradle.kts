import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        implementation(projects.shared.composeRuntimePublic)

        api(projects.shared.accountPublic)
        api(projects.shared.analyticsPublic)
        api(projects.shared.authPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.coroutinesPublic)
        api(projects.shared.emailPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.featureFlagPublic)
        api(projects.shared.firmwarePublic)
        api(projects.shared.fwupPublic)
        api(projects.shared.homePublic)
        api(projects.shared.keyboxPublic)
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.databasePublic)
        api(projects.shared.moneyPublic)
        api(projects.shared.notificationsPublic)
        api(projects.shared.onboardingPublic)
        api(projects.shared.queueProcessorPublic)
        api(projects.shared.phoneNumberPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.mobilePayPublic)
        api(projects.shared.routerPublic)
        api(projects.shared.stateMachineFrameworkPublic)
        implementation(projects.shared.composeRuntimePublic)
        implementation(projects.shared.loggingPublic)
        implementation(libs.kmp.molecule.runtime)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.authFake)
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.bitcoinTesting)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.cloudBackupFake)
        implementation(projects.shared.cloudStoreFake)
        implementation(projects.shared.emailFake)
        implementation(projects.shared.emergencyAccessKitFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.firmwareFake)
        implementation(projects.shared.fwupFake)
        implementation(projects.shared.homeFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.moneyFake)
        implementation(projects.shared.moneyTesting)
        implementation(projects.shared.nfcFake)
        implementation(projects.shared.notificationsFake)
        implementation(projects.shared.onboardingFake)
        implementation(projects.shared.phoneNumberFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.queueProcessorFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.mobilePayFake)
        implementation(projects.shared.stateMachineDataFake)
        implementation(projects.shared.stateMachineFrameworkFake)
        implementation(projects.shared.stateMachineFrameworkTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.timeFake)
        implementation(projects.shared.ktorTestFake)
      }
    }

    val commonIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.bitcoinTesting)
        implementation(projects.shared.moneyTesting)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.bitkeyPrimitivesPublic)
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.shared.stateMachineDataTesting)
        implementation(projects.shared.stateMachineFrameworkTesting)
      }
    }
  }
}
