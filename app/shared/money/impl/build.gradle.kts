import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountPublic)
        api(projects.shared.availabilityPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.ktorClientPublic)
        api(projects.shared.databasePublic)
        implementation(projects.shared.loggingPublic)
      }
    }
    commonTest {
      dependencies {
        // TODO: remove dependency on :impl.
        implementation(projects.shared.amountImpl) {
          because("Depends on DoubleFormatterImpl.")
        }
        implementation(projects.shared.amountFake)
        implementation(projects.shared.accountFake)
        implementation(projects.shared.debugFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.moneyFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.workerFake)
      }
    }
  }
}
