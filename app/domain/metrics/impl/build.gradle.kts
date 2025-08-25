
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
        implementation(libs.kmp.kotlin.datetime)
        implementation(projects.domain.workerPublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.libs.datadogPublic)
      }
    }
    commonTest {
      dependencies {
        implementation(projects.libs.datadogFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.metricsFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.timeFake)
      }
    }
  }
}
