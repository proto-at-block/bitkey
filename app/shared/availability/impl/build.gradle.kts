import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.debugPublic)
        api(projects.shared.f8eClientPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.debugFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.timeFake)
        implementation(projects.shared.workerFake)
      }
    }
  }
}
