import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.resultPublic)
        api(libs.kmp.okio)
      }
    }

    val androidMain by getting {
      dependencies {
        api(libs.android.firebase.messaging)
      }
    }
  }
}
