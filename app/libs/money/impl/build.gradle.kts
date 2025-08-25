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
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.availabilityPublic)
        implementation(projects.domain.debugPublic)
        implementation(projects.domain.f8eClientPublic)
        implementation(projects.libs.ktorClientPublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.libs.loggingPublic)
      }
    }
    commonTest {
      dependencies {
        // TODO: remove dependency on :impl.
        implementation(projects.libs.amountImpl) {
          because("Depends on DoubleFormatterImpl.")
        }
        implementation(projects.domain.accountFake)
        implementation(projects.domain.debugFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.workerFake)
        // TODO: remove dependency on :impl.
        implementation(projects.libs.amountImpl) {
          because("Depends on DoubleFormatterImpl")
        }
      }
    }
  }
}
