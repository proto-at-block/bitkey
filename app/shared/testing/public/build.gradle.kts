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
        api(libs.kmp.kotlin.coroutines)
        api(libs.kmp.test.kotest.assertions)
        api(libs.kmp.test.kotest.assertions.json)
        api(libs.kmp.test.kotest.framework.api)
        api(libs.kmp.test.kotlin.coroutines)
        api(libs.kmp.test.turbine)
        api(projects.shared.loggingTesting)
      }
    }
  }
}
