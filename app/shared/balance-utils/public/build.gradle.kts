import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.priceChartPublic)
        api(projects.domain.walletPublic)
        api(libs.kmp.kotlin.datetime)
      }
    }
  }
}
