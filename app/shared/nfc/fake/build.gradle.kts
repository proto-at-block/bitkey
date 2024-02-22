import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.firmwareFake)
        implementation(projects.shared.keyValueStorePublic)
      }
    }
  }
}
