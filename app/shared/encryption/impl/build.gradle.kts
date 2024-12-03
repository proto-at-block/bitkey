import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.shared.stdlibPublic)
        api(libs.kmp.kotlin.result)
        api(projects.shared.resultPublic)
        api(projects.shared.secureEnclavePublic)
      }
    }
    val commonJvmMain by getting {
      dependencies {
        implementation(projects.rust.coreFfi)
      }
    }
    val androidInstrumentedTest by getting {
      dependencies {
        implementation(libs.jvm.test.junit)
        implementation(libs.android.test.junit)
        implementation(libs.android.test.junit.ktx)
        implementation(libs.android.test.espresso.core)
        implementation(projects.shared.secureEnclaveImpl)
      }
    }
    commonTest {
      dependencies {
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.secureEnclaveFake)
        implementation(libs.kmp.okio)
      }
    }
  }
}
