import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.platformPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.kmp.crashkios.bugsnag)
        implementation(libs.android.bugsnag)
      }
    }

    val iosMain by getting {
      dependencies {
        implementation(libs.kmp.crashkios.bugsnag)
      }
    }
  }
}
