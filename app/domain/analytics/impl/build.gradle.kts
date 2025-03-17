import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.gradle.exclude

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
        api(libs.kmp.kotlin.datetime)
        api(projects.domain.accountPublic)
        api(projects.domain.bitcoinPublic)
        api(projects.domain.debugPublic)
        api(projects.libs.keyValueStorePublic)
        api(projects.libs.queueProcessorPublic)
        api(projects.libs.platformPublic)
        api(projects.libs.timePublic)
        api(projects.domain.databasePublic)
        api(projects.domain.firmwarePublic)
        // TODO: remove dependency on :impl
        implementation(projects.libs.queueProcessorImpl) {
          because("Depends on PeriodicProcessorImpl")
        }
        implementation(libs.kmp.settings)
        // TODO: break impl dependency.
        implementation(projects.libs.queueProcessorImpl)
      }
    }

    commonTest {
      dependencies {
        api(libs.kmp.kotlin.datetime)
        api(projects.domain.accountFake)
        api(projects.domain.analyticsFake) {
          exclude(projects.domain.analyticsPublic)
        }
        api(projects.domain.bitcoinFake)
        api(projects.domain.f8eClientFake)
        api(projects.domain.featureFlagFake)
        api(projects.domain.keyboxFake)
        api(projects.libs.keyValueStoreFake)
        api(projects.libs.moneyFake)
        api(projects.libs.platformFake)
        api(projects.libs.timeFake)
        implementation(projects.domain.debugFake)
        implementation(projects.libs.queueProcessorFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
