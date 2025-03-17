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
        api(projects.libs.platformPublic)
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
