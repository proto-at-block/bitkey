import build.wallet.gradle.logic.extensions.targets
import build.wallet.gradle.logic.gradle.exclude

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.coroutines)
        implementation(projects.shared.loggingPublic)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kmp.test.kotest.assertions)
        implementation(projects.shared.kotestPublic)
        implementation(projects.shared.coroutinesTesting) {
          exclude(projects.shared.coroutinesPublic)
        }
      }
    }
  }
}
