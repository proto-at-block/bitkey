import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.extensions.buildLogic

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kmp.okio)
        implementation(projects.libs.keyValueStorePublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.platformFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.timeFake)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.browser)
        implementation(libs.android.core.ktx)
        implementation(libs.android.pdfbox)
        implementation(libs.android.biometric)
        api(libs.android.age.signals)
        implementation(libs.android.google.play.services.coroutines)
      }
    }
  }
}

buildLogic {
  android {
    buildFeatures {
      androidResources = true
    }
  }
}
