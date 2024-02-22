import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.databasePublic)
        api(projects.shared.f8eClientPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.timeFake)
      }
    }
  }
}
