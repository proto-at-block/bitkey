import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.extensions.commonIntegrationTest
import build.wallet.gradle.logic.extensions.invoke

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.databasePublic)
        api(projects.shared.inAppSecurityPublic)
        api(projects.shared.inheritancePublic)
        api(projects.shared.relationshipsPublic)
        api(projects.shared.platformPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.authFake)
        implementation(projects.shared.availabilityFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.cloudBackupFake)
        implementation(projects.shared.cloudStoreFake)
        implementation(projects.shared.debugFake)
        implementation(projects.shared.firmwareFake)
        implementation(projects.shared.fwupFake)
        implementation(projects.shared.homeFake)
        implementation(projects.shared.inAppSecurityFake)
        implementation(projects.shared.inheritanceFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.mobilePayFake)
        implementation(projects.shared.moneyFake)
        implementation(projects.shared.notificationsFake)
        implementation(projects.shared.onboardingFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.relationshipsFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
      }
    }

    commonIntegrationTest {
      dependencies {
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.shared.stateMachineFrameworkTesting)
        implementation(projects.shared.stateMachineUiTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
