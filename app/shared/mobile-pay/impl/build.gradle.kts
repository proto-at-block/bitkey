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
        api(libs.kmp.kotlin.datetime)
        api(libs.kmp.okio)
        api(projects.shared.accountPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.moneyPublic)
        api(projects.shared.ktorClientPublic)
        api(projects.shared.databasePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.mobilePayFake)
        implementation(projects.shared.moneyFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.timeFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.coroutinesTesting)
      }
    }
  }
}
