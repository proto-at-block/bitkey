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
        api(projects.shared.accountPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.debugPublic)
        api(projects.shared.f8eClientPublic)
        // TODO: break impl dependency.
        implementation(projects.shared.f8eClientImpl)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.debugFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.timeFake)
        implementation(projects.shared.workerFake)
      }
    }
  }
}
