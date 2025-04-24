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
        api(projects.libs.contactMethodPublic)
        api(projects.domain.databasePublic)
        api(projects.libs.keyValueStorePublic)
        api(projects.domain.f8eClientPublic)
        implementation(projects.libs.loggingPublic)
        // TODO: remove dependency on :impl
        implementation(projects.libs.queueProcessorImpl) {
          because("Depends on PeriodicProcessorImpl")
        }
        api(projects.domain.walletPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.domain.notificationsFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.contactMethodFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
