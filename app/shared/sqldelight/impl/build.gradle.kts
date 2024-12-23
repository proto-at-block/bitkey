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
        implementation(projects.shared.resultPublic)
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.platformPublic)
        api(libs.kmp.sqldelight.runtime)
        implementation(libs.kmp.settings)
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(libs.jvm.sqldelight.driver)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.sqlcipher)
        implementation(libs.android.sqlite)
        implementation(libs.android.sqldelight.driver)
      }
    }

    val iosMain by getting {
      dependencies {
        implementation(libs.native.sqldelight.driver)
        implementation(libs.native.sqliter)
      }
    }
  }
}
