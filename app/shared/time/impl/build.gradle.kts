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
      }
    }
    commonTest {
      dependencies {
        implementation(projects.shared.platformFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.timeFake)
      }
    }
  }
}
