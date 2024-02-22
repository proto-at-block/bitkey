import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.okio)
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.nfcPublic)
        implementation(projects.shared.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        api(projects.shared.platformFake)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.browser)
        implementation(libs.android.core.ktx)
        implementation(libs.android.pbfbox)
      }
    }
  }
}
