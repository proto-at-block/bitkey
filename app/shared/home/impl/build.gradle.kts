import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.bitcoinPublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.mobilePayPublic)
        api(projects.shared.databasePublic)
      }
    }

    commonTest {
      dependencies {
        api(projects.shared.bitcoinFake)
        api(projects.shared.homeFake)
        api(projects.shared.mobilePayFake)
        implementation(projects.shared.sqldelightTesting)
      }
    }
  }
}
