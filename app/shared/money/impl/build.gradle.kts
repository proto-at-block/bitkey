import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  id("build.wallet.redacted")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountPublic)
        api(projects.shared.availabilityPublic)
        api(projects.shared.debugPublic)
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
        implementation(projects.shared.accountFake)
        implementation(projects.shared.debugFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.moneyFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.workerFake)
        // TODO: remove dependency on :impl.
        implementation(projects.shared.amountImpl) {
          because("Depends on DoubleFormatterImpl")
        }
      }
    }
  }
}
