import build.wallet.gradle.logic.extensions.commonIntegrationTest
import build.wallet.gradle.logic.extensions.invoke
import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountPublic)
        api(projects.shared.authPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.cloudStorePublic)
        api(projects.shared.databasePublic)
        api(projects.shared.firmwarePublic)
        api(projects.shared.fwupPublic)
        api(projects.shared.homePublic)
        api(projects.shared.notificationsPublic)
        api(projects.shared.onboardingPublic)
        api(projects.shared.mobilePayPublic)
        implementation(projects.shared.accountFake)
        implementation(projects.shared.loggingPublic)
        implementation(projects.shared.keyValueStorePublic)
        implementation(projects.shared.recoveryPublic)
        implementation(libs.kmp.settings)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.authFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.cloudBackupFake)
        implementation(projects.shared.cloudStoreFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.firmwareFake)
        implementation(projects.shared.fwupFake)
        implementation(projects.shared.homeFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.notificationsFake)
        implementation(projects.shared.onboardingFake)
        implementation(projects.shared.mobilePayFake)
        implementation(projects.shared.moneyFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.sqldelightFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.availabilityImpl)
      }
    }

    commonIntegrationTest {
      dependencies {
        implementation(projects.shared.moneyTesting)
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.shared.stateMachineUiTesting)
        implementation(projects.shared.stateMachineFrameworkTesting)
      }
    }
  }
}
