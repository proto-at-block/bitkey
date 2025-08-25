import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.libs.stdlibPublic)
        implementation(libs.kmp.kotlin.result)
        implementation(projects.libs.secureEnclavePublic)
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
        implementation(projects.libs.secureEnclaveImpl)
      }
    }
    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.secureEnclaveFake)
        implementation(libs.kmp.okio)
      }
    }
  }
}
