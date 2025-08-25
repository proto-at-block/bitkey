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
        implementation(libs.kmp.kermit)
        implementation(projects.domain.accountPublic)
        implementation(projects.libs.platformPublic)
        implementation(projects.domain.hardwarePublic)
        implementation(libs.kmp.kotlin.coroutines)
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(projects.libs.datadogPublic)
        implementation(libs.android.datadog.logs)
        implementation(libs.kmp.kermit.bugsnag)
      }
    }

    val iosMain by getting {
      dependencies {
        implementation(libs.kmp.kermit.bugsnag)
      }
    }

    commonTest.dependencies {
      implementation(projects.libs.testingPublic)
    }
  }
}
