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
        api(projects.domain.databasePublic)
        api(projects.libs.moneyPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
