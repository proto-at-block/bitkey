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
        api(projects.domain.accountPublic)
        implementation(projects.libs.loggingPublic)
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
