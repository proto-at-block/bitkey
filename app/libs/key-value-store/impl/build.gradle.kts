import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
      }
      dependencies {
        api(projects.libs.platformPublic)
        api(libs.kmp.settings)
        implementation(libs.kmp.settings.coroutines)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.datastore.preferences)
        implementation(libs.android.security.cryptography)
        implementation(libs.kmp.settings.datastore)
      }
    }

    val jvmTest by getting {
      dependencies {
        implementation(projects.libs.platformImpl)
      }
    }
  }
}
