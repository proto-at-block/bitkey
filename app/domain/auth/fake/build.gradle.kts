import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        api(projects.domain.bitcoinFake)
        api(projects.domain.bitkeyPrimitivesFake)
      }
    }
  }
}
