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
        implementation(libs.kmp.kotlin.datetime)
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.debugPublic)
        implementation(projects.libs.keyValueStorePublic)
        implementation(projects.libs.queueProcessorPublic)
        implementation(projects.libs.platformPublic)
        implementation(projects.libs.timePublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.domain.hardwarePublic)
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
        implementation(libs.kmp.kotlin.datetime)
        implementation(projects.domain.accountFake)
        implementation(projects.domain.analyticsFake) {
          exclude(projects.domain.analyticsPublic)
        }
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.timeFake)
        implementation(projects.domain.debugFake)
        implementation(projects.libs.queueProcessorFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
