import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.gradle.exclude

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.datetime)
        api(projects.shared.accountPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.queueProcessorPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.timePublic)
        api(projects.shared.databasePublic)
        api(projects.shared.firmwarePublic)
        // TODO: remove dependency on :impl
        implementation(projects.shared.queueProcessorImpl) {
          because("Depends on PeriodicProcessorImpl")
        }
        implementation(libs.kmp.settings)
      }
    }

    commonTest {
      dependencies {
        api(libs.kmp.kotlin.datetime)
        api(projects.shared.accountFake)
        api(projects.shared.analyticsFake) {
          exclude(projects.shared.analyticsPublic)
        }
        api(projects.shared.bitcoinFake)
        api(projects.shared.f8eClientFake)
        api(projects.shared.featureFlagFake)
        api(projects.shared.keyboxFake)
        api(projects.shared.keyValueStoreFake)
        api(projects.shared.moneyFake)
        api(projects.shared.platformFake)
        api(projects.shared.timeFake)
        implementation(projects.shared.debugFake)
        implementation(projects.shared.queueProcessorFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
