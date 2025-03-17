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
        api(projects.domain.bitcoinPublic)
        api(projects.libs.loggingPublic)
        api(projects.domain.mobilePayPublic)
        api(projects.domain.databasePublic)
      }
    }

    commonTest {
      dependencies {
        api(projects.domain.bitcoinFake)
        api(projects.domain.homeFake)
        api(projects.domain.mobilePayFake)
        implementation(projects.libs.sqldelightTesting)
      }
    }
  }
}
