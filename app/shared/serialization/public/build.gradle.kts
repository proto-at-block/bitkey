import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.resultPublic)
        api(libs.kmp.okio)
        api(libs.kmp.kotlin.serialization.core)
        api(libs.kmp.kotlin.serialization.json)
        api(libs.kmp.matthewnelson.encoding)
        implementation(libs.kmp.big.number)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.testingPublic)
        implementation(libs.kmp.test.kotest.framework.engine)
      }
    }
  }
}
