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
        implementation(projects.domain.databasePublic)
        implementation(projects.libs.moneyPublic)
        implementation(projects.shared.balanceUtilsImpl)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.timeFake)
      }
    }
  }
}
