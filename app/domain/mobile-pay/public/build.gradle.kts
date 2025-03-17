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
        api(projects.domain.bitkeyPrimitivesPublic)
        api(projects.domain.bitcoinPublic)
        api(projects.domain.featureFlagPublic)
        api(projects.libs.moneyPublic)
        api(projects.shared.workerPublic)
        implementation(libs.kmp.kotlin.serialization.core)
        implementation(libs.kmp.kotlin.serialization.json)
        implementation(projects.libs.timePublic)
      }
    }
  }
}
