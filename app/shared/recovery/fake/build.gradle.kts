import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.authFake)
        api(projects.shared.bitcoinPrimitivesFake)
        api(projects.shared.timeFake)
        api(projects.shared.f8eClientFake)
      }
    }
  }
}
