import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.shared.databasePublic)
        implementation(projects.shared.resultPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.timeFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.partnershipsFake)
      }
    }
  }
}
