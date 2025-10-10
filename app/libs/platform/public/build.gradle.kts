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
        api(libs.kmp.kotlin.result)
        api(libs.kmp.okio)
        api(projects.domain.workerPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        api(libs.android.firebase.messaging)
      }
    }
  }
}
