import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.platformPublic)
      }
    }

    val commonJvmMain by getting {
      dependencies {
        api(libs.android.lib.phone.number)
      }
    }

    val jvmTest by getting {
      dependencies {
        api(projects.shared.platformFake)
      }
    }
  }
}
