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
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.sqldelightTesting) {
          exclude(projects.libs.sqldelightPublic)
        }
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
