import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.databasePublic)
        api(projects.shared.platformPublic)
      }
    }

    commonTest {
      dependencies {
        api(projects.shared.sqldelightTesting)
        api(projects.shared.testingPublic)
      }
    }
  }
}
