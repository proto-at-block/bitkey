import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.cloudBackupPublic)
        api(projects.libs.cloudStorePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
