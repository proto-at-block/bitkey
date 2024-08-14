import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.priceChartPublic)
        implementation(projects.shared.databasePublic)
        implementation(projects.shared.moneyPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.coroutinesTesting)
        // implementation(projects.shared.priceChartFake)
      }
    }
  }
}
