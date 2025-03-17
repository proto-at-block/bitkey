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
        api(projects.libs.queueProcessorPublic)
        api(projects.domain.databasePublic)
        api(projects.libs.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        api(projects.libs.queueProcessorFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.queueProcessorTesting)
      }
    }
  }
}
