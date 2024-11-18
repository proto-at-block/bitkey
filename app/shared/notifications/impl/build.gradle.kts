import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.phoneNumberPublic)
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
        implementation(projects.shared.emailFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.notificationsFake)
        implementation(projects.shared.phoneNumberFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
