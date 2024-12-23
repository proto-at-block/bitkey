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
        api(projects.shared.platformPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.debugPublic)
        implementation(projects.shared.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.debugFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
