import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

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
