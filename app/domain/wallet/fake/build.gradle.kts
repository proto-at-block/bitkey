import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.bitcoinPrimitivesFake)
        implementation(projects.libs.bdkBindingsFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.libs.timeFake)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kmp.test.kotest.assertions)
        implementation(projects.domain.walletTesting) {
//          exclude(projects.domain.walletPublic)
        }
        implementation(projects.libs.bitcoinPrimitivesFake)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
