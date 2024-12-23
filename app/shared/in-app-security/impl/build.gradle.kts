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
        implementation(projects.shared.databasePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.inAppSecurityFake)
      }
    }
  }
}
