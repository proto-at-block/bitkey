import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.moneyPublic)
        implementation(libs.kmp.test.kotest.assertions)
      }
    }
  }
}