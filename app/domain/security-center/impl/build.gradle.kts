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
        implementation(projects.libs.loggingPublic)
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
      }
    }
  }
}
