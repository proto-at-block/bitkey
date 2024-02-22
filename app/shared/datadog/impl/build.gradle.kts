import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.platformPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.datadog.logs)
        implementation(libs.android.datadog.rum)
        implementation(libs.android.datadog.trace)
      }
    }
  }
}
