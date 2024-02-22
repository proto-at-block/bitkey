import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    val commonJvmMain by getting {
      dependencies {
        implementation(libs.jvm.sqldelight.driver)
      }
    }

    val iosMain by getting {
      dependencies {
        implementation(libs.native.sqldelight.driver)
      }
    }
  }
}
