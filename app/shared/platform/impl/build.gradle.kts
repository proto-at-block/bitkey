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
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.nfcPublic)
        implementation(projects.shared.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.platformFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.timeFake)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.browser)
        implementation(libs.android.core.ktx)
        implementation(libs.android.pbfbox)
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
