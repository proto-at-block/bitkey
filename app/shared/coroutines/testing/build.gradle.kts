import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.coroutines)
        api(libs.kmp.test.kotest.framework.api)
        api(libs.kmp.test.kotlin.coroutines)
        api(libs.kmp.test.turbine)
        implementation(libs.kmp.test.kotest.assertions)
        implementation(projects.shared.loggingPublic)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
