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
        implementation(projects.domain.cloudBackupPublic)
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.inAppSecurityPublic)
        implementation(projects.domain.inheritancePublic)
        implementation(projects.domain.recoveryPublic)
        implementation(projects.domain.notificationsPublic)
        implementation(projects.domain.privilegedActionsPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.domain.metricsPublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.domain.featureFlagPublic)
      }
    }
    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.accountFake)
        implementation(projects.domain.hardwareFake)
        implementation(projects.domain.inAppSecurityFake)
        implementation(projects.domain.inheritanceFake)
        implementation(projects.domain.recoveryFake)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.domain.notificationsFake)
        implementation(projects.domain.onboardingFake)
        implementation(projects.domain.securityCenterFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.metricsFake)
        implementation(projects.domain.privilegedActionsFake)
        implementation(projects.domain.privilegedActionsImpl)
        implementation(projects.domain.availabilityFake)
        implementation(projects.domain.featureFlagFake)
      }
    }
  }
}
