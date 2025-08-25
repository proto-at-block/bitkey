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
        implementation(compose.runtime)
        implementation(projects.libs.composeRuntimePublic)

        implementation(projects.domain.accountPublic)
        implementation(projects.domain.analyticsPublic)
        implementation(projects.domain.authPublic)
        implementation(projects.domain.cloudBackupPublic)
        implementation(projects.domain.f8eClientPublic)
        implementation(projects.domain.featureFlagPublic)
        implementation(projects.domain.hardwarePublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.libs.keyValueStorePublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.libs.moneyPublic)
        implementation(projects.domain.notificationsPublic)
        implementation(projects.domain.onboardingPublic)
        implementation(projects.libs.queueProcessorPublic)
        implementation(projects.libs.contactMethodPublic)
        implementation(projects.libs.platformPublic)
        implementation(projects.domain.mobilePayPublic)
        implementation(projects.ui.routerPublic)
        implementation(projects.libs.stateMachinePublic)
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
        implementation(projects.domain.walletFake)
        implementation(projects.domain.walletTesting)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.domain.debugFake)
        implementation(projects.domain.emergencyExitKitFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.hardwareFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.libs.moneyTesting)
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
        implementation(projects.domain.walletTesting)
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
