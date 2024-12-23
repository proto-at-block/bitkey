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
        api(projects.shared.queueProcessorPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        api(projects.shared.queueProcessorFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.queueProcessorTesting)
      }
    }
  }
}
