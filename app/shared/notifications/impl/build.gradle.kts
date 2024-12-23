import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.contactMethodPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.f8eClientPublic)
        implementation(projects.shared.loggingPublic)
        // TODO: remove dependency on :impl
        implementation(projects.shared.queueProcessorImpl) {
          because("Depends on PeriodicProcessorImpl")
        }
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.notificationsFake)
        implementation(projects.shared.contactMethodFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
