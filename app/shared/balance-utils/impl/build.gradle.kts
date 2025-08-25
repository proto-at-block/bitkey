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
        implementation(projects.shared.balanceUtilsPublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.libs.keyValueStorePublic)
        implementation(projects.libs.platformPublic)
        implementation(libs.kmp.settings.coroutines)
        implementation(libs.kmp.kotlin.datetime)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
