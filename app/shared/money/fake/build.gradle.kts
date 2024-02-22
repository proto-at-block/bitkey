import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.big.number)
        api(libs.kmp.kotlin.datetime)
        api(projects.shared.amountPublic)
        api(projects.shared.platformFake)
      }
    }
  }
}
