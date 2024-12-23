import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.databasePublic)
        api(projects.shared.moneyPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
