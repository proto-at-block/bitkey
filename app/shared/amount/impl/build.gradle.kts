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
        api(projects.shared.platformPublic)
        implementation(libs.kmp.big.number)
      }
    }
    commonTest {
      dependencies {
        api(projects.shared.platformFake)
      }
    }
  }
}
