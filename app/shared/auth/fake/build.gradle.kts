import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        api(projects.shared.bitcoinFake)
        api(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.f8eClientFake)
      }
    }
  }
}