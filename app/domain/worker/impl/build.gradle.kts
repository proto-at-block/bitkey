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
        api(projects.domain.inheritancePublic)
        api(projects.domain.notificationsPublic)
        api(projects.domain.metricsPublic)
        api(projects.libs.queueProcessorPublic)
        api(projects.domain.recoveryPublic)
        api(projects.domain.relationshipsPublic)

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
