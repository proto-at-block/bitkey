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
        implementation(projects.domain.inheritancePublic)
        implementation(projects.domain.notificationsPublic)
        implementation(projects.domain.metricsPublic)
        implementation(projects.libs.queueProcessorPublic)
        implementation(projects.domain.privilegedActionsPublic)
        implementation(projects.domain.recoveryPublic)
        implementation(projects.domain.relationshipsPublic)
        implementation(projects.domain.walletPublic)

        implementation(projects.libs.loggingPublic)
        implementation(projects.domain.securityCenterPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.workerFake)
        implementation(projects.libs.platformFake)
      }
    }
  }
}
