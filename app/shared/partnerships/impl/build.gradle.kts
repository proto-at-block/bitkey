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
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.timeFake)
      }
    }
  }
}
