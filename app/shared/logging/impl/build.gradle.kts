import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kermit)
        api(projects.shared.accountPublic)
        api(projects.shared.platformPublic)
      }
    }
    val androidMain by getting {
      dependencies {
        api(projects.shared.datadogPublic)
        api(libs.android.datadog.logs)
        implementation(libs.kmp.kermit.bugsnag)
      }
    }

    val iosMain by getting {
      dependencies {
        implementation(libs.kmp.kermit.bugsnag)
      }
    }
  }
}
