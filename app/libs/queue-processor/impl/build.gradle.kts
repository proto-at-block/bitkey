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
        implementation(projects.libs.queueProcessorPublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.libs.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.queueProcessorFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.queueProcessorTesting)
        implementation(projects.libs.platformFake)
      }
    }
  }
}
