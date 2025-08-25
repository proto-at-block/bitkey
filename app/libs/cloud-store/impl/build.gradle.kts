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
        implementation(libs.kmp.okio)
        implementation(projects.libs.platformPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.google.api.client)
        implementation(libs.android.google.auth)
        implementation(libs.jvm.google.drive)
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
