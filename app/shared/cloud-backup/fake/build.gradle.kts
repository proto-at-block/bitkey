import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.bitkeyPrimitivesFake)
        api(projects.shared.encryptionFake)
        api(projects.shared.recoveryFake)
        implementation(libs.bundles.kmp.test.kotest)
      }
    }
  }
}
