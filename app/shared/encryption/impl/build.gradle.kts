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
      }
    }
    val commonJvmMain by getting {
      dependencies {
        implementation(projects.core)
      }
    }
    commonTest {
      dependencies {
        implementation(projects.shared.testingPublic)
        implementation(libs.kmp.okio)
      }
    }
  }
}
