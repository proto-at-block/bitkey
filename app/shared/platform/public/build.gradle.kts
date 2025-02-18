import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    all {
      languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }
    commonMain {
      dependencies {
        api(projects.shared.resultPublic)
        api(libs.kmp.okio)
        implementation(projects.shared.stdlibPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        api(libs.android.firebase.messaging)
      }
    }
  }
}
