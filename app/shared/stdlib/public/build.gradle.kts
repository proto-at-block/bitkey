import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      /**
       * Note! This module is meant to contain absolute *minimum* amount of dependencies and no
       * dependencies on other modules.
       */
      dependencies {
        api(libs.kmp.kotlin.coroutines)
        api(libs.kmp.kotlin.result)
        api(libs.kmp.okio)
        implementation(libs.kmp.big.number)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
