import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.datetime)
        api(libs.kmp.okio)
        api(libs.kmp.big.number)
        api(projects.shared.bitkeyPrimitivesPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.f8ePublic)
        api(projects.shared.featureFlagPublic)
        api(projects.shared.dbResultPublic)
        api(projects.shared.moneyPublic)
        implementation(libs.kmp.kotlin.serialization.core)
        implementation(libs.kmp.kotlin.serialization.json)
        implementation(projects.shared.timePublic)
      }
    }
  }
}
