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
        api(libs.kmp.kotlin.datetime)
        api(libs.kmp.okio)
        api(libs.kmp.sqldelight.async)
        api(libs.kmp.sqldelight.coroutines)
        api(libs.kmp.sqldelight.runtime)
        api(libs.kmp.wire.runtime)
        api(projects.shared.dbResultPublic)
        implementation(projects.shared.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.sqldelightTesting) {
          exclude(projects.shared.sqldelightPublic)
        }
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
