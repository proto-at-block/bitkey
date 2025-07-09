import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.extensions.commonIntegrationTest
import build.wallet.gradle.logic.extensions.invoke

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.databasePublic)
        api(projects.domain.inAppSecurityPublic)
        api(projects.domain.inheritancePublic)
        api(projects.domain.metricsPublic)
        api(projects.domain.relationshipsPublic)
        api(projects.domain.notificationsPublic)
        api(projects.domain.walletPublic)
        api(projects.libs.platformPublic)
        api(projects.domain.securityCenterPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.authFake)
        implementation(projects.domain.availabilityFake)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.domain.debugFake)
        implementation(projects.domain.hardwareFake)
        implementation(projects.domain.inAppSecurityFake)
        implementation(projects.domain.inheritanceFake)
        implementation(projects.domain.metricsFake)
        implementation(projects.domain.mobilePayFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.domain.notificationsFake)
        implementation(projects.domain.onboardingFake)
        implementation(projects.domain.recoveryFake)
        implementation(projects.domain.walletFake)
        implementation(projects.domain.relationshipsFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.securityCenterFake)
        implementation(projects.domain.coachmarkFake)
      }
    }

    commonIntegrationTest {
      dependencies {
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.libs.stateMachineTesting)
        implementation(projects.ui.featuresTesting)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
