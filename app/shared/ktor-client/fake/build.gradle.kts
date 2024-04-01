import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kmp.ktor.client.core)
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(libs.jvm.ktor.client.okhttp)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.jvm.ktor.client.okhttp)
      }
    }

    val iosMain by getting {
      dependencies {
        implementation(libs.native.ktor.client.darwin)
      }
    }
  }
}
