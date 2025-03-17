import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
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
        api(projects.libs.resultPublic)
        api(libs.kmp.kotlin.coroutines)
        api(libs.kmp.kotlin.result)
        api(libs.kmp.kotlin.serialization.core)
        api(libs.kmp.kotlin.serialization.json)
        api(libs.kmp.okio)
        implementation(libs.kmp.big.number)
        implementation(libs.kmp.matthewnelson.encoding)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
