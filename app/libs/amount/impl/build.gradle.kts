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
        implementation(projects.libs.platformPublic)
        implementation(libs.kmp.big.number)
      }
    }
    commonTest {
      dependencies {
        implementation(projects.libs.platformFake)
      }
    }
  }
}
