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
        api(libs.kmp.settings)
        implementation(libs.kmp.settings.coroutines)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.datastore.preferences)
        implementation(libs.android.security.cryptography)
        implementation(libs.kmp.settings.datastore)
      }
    }

    val jvmTest by getting {
      dependencies {
        implementation(projects.shared.platformImpl)
      }
    }
  }
}
