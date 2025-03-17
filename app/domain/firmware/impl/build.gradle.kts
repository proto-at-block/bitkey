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
        api(projects.domain.firmwarePublic)
        api(projects.libs.loggingPublic)
        api(projects.domain.databasePublic)
        api(projects.libs.queueProcessorPublic)
        implementation(projects.libs.stdlibPublic)
        // TODO: remove dependency on :impl
        implementation(projects.libs.queueProcessorImpl) {
          because("Depends on PeriodicProcessorImpl")
        }
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.sqldelightTesting)
        api(projects.libs.memfaultFake)
        implementation(projects.libs.testingPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(projects.rust.firmwareFfi)
      }
    }
  }
}
