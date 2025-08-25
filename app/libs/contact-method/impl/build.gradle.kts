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
        implementation(projects.libs.platformPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    val commonJvmMain by getting {
      dependencies {
        implementation(libs.android.lib.phone.number)
      }
    }

    val jvmTest by getting {
      dependencies {
        implementation(projects.libs.platformFake)
      }
    }
  }
}
