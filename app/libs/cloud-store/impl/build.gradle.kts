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
        api(libs.kmp.okio)
        api(projects.libs.platformPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        api(libs.android.google.api.client)
        api(libs.android.google.auth)
        api(libs.jvm.google.drive)
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(projects.libs.keyValueStorePublic)
        implementation(libs.kmp.settings)
      }
    }
  }
}
