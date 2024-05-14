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
        api(projects.shared.resultPublic)
        api(projects.shared.stdlibPublic)
        api(libs.kmp.kotlin.datetime)
        implementation(projects.shared.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.timeFake) {
          exclude(projects.shared.timePublic)
        }
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
