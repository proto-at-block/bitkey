import build.wallet.gradle.logic.extensions.allTargets

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
        api(compose.runtime)
        implementation(projects.libs.composeRuntimePublic)

        api(projects.domain.accountPublic)
        api(projects.domain.analyticsPublic)
        api(projects.domain.authPublic)
        api(projects.domain.bitcoinPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.domain.featureFlagPublic)
        api(projects.domain.firmwarePublic)
        api(projects.domain.fwupPublic)
        api(projects.domain.homePublic)
        api(projects.domain.keyboxPublic)
        api(projects.libs.keyValueStorePublic)
        api(projects.domain.databasePublic)
        api(projects.libs.moneyPublic)
        api(projects.domain.notificationsPublic)
        api(projects.domain.onboardingPublic)
        api(projects.libs.queueProcessorPublic)
        api(projects.libs.contactMethodPublic)
        api(projects.libs.platformPublic)
        api(projects.domain.mobilePayPublic)
        api(projects.ui.routerPublic)
        api(projects.libs.stateMachinePublic)
        implementation(projects.libs.composeRuntimePublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.domain.relationshipsPublic)
        implementation(libs.kmp.molecule.runtime)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.authFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.bitcoinFake)
        implementation(projects.domain.bitcoinTesting)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.domain.debugFake)
        implementation(projects.domain.emergencyAccessKitFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.firmwareFake)
        implementation(projects.domain.fwupFake)
        implementation(projects.domain.homeFake)
        implementation(projects.domain.keyboxFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.libs.moneyTesting)
        implementation(projects.domain.nfcFake)
        implementation(projects.domain.notificationsFake)
        implementation(projects.domain.onboardingFake)
        implementation(projects.libs.contactMethodFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.queueProcessorFake)
        implementation(projects.domain.recoveryFake)
        implementation(projects.domain.mobilePayFake)
        implementation(projects.domain.dataStateMachineFake)
        implementation(projects.libs.stateMachineFake)
        implementation(projects.libs.stateMachineTesting)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.timeFake)
        implementation(projects.libs.ktorClientFake)
      }
    }

    val commonIntegrationTest by getting {
      dependencies {
        implementation(projects.domain.bitcoinTesting)
        implementation(projects.libs.moneyTesting)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.bitkeyPrimitivesPublic)
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.libs.stateMachineTesting)
      }
    }
  }
}
