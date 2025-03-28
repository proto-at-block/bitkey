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
        api(libs.kmp.okio)
        api(projects.libs.keyValueStorePublic)
        api(projects.libs.loggingPublic)
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
