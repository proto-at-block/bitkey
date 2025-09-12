import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.result)
        api(projects.domain.walletPublic)
        api(projects.libs.bitcoinPrimitivesPublic)
      }
    }
  }
}
