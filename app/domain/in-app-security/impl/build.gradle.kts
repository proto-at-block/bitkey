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
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.analyticsFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.inAppSecurityFake)
      }
    }
  }
}
