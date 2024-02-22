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
        api(projects.shared.bitcoinFake)
        api(projects.shared.bitkeyPrimitivesFake)
      }
    }
    commonTest {
      dependencies {
        implementation(projects.shared.coroutinesTesting) {
          exclude(projects.shared.coroutinesPublic)
        }
      }
    }
  }
}
