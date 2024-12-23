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
