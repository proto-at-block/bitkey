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
        api(projects.domain.featureFlagPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.domain.accountFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.libs.timeFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.coachmarkFake)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
