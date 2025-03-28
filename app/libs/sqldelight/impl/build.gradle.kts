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
        api(projects.libs.keyValueStorePublic)
        api(projects.libs.loggingPublic)
        api(projects.libs.platformPublic)
        api(libs.kmp.kotlin.result)
        api(libs.kmp.sqldelight.runtime)
        implementation(libs.kmp.settings)
        implementation(projects.libs.stdlibPublic)
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
