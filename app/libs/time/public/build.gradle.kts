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
        api(projects.libs.resultPublic)
        api(projects.libs.stdlibPublic)
        api(libs.kmp.kotlin.datetime)
        implementation(projects.libs.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.timeFake) {
          exclude(projects.libs.timePublic)
        }
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
