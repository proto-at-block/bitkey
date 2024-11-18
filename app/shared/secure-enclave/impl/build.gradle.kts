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
        implementation(projects.shared.loggingPublic)
      }
    }

    commonTest {
      dependencies {
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.core.ktx)
      }
    }

    val iosMain by getting {
      dependencies {
      }
    }

    val androidInstrumentedTest by getting {
      dependencies {
        implementation(libs.jvm.test.junit)
        implementation(libs.android.test.junit)
        implementation(libs.android.test.junit.ktx)
        implementation(libs.android.test.espresso.core)
      }
    }
  }
}
