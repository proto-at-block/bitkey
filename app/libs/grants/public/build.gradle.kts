import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kmp.okio)
        api(projects.libs.loggingPublic)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.kmp.test.kotest.assertions)
        implementation(libs.kmp.test.kotest.framework.engine)
      }
    }
  }
}
