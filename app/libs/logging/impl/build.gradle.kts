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
        api(libs.kmp.kermit)
        api(projects.domain.accountPublic)
        api(projects.libs.platformPublic)
        api(projects.domain.firmwarePublic)
      }
    }
    val androidMain by getting {
      dependencies {
        api(projects.libs.datadogPublic)
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
