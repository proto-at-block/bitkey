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
        api(projects.shared.bitcoinPrimitivesFake)
        implementation(projects.shared.bdkBindingsFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.timeFake)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kmp.test.kotest.assertions)
        implementation(projects.shared.bitcoinTesting) {
          exclude(projects.shared.bitcoinPublic)
        }
        implementation(projects.shared.bitcoinPrimitivesFake)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
