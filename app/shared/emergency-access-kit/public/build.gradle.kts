import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.cloudStorePublic)
        api(projects.shared.f8ePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kmp.test.kotest.assertions.json)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
