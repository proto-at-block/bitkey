import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.coroutines.native)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.bitkeyPrimitivesPublic)
        api(projects.shared.dbResultPublic)
        api(projects.shared.f8ePublic)
      }
    }
  }
}
