import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.resultPublic)
        api(libs.kmp.big.number)
        api(libs.kmp.kotlin.datetime)
      }
    }
  }
}
