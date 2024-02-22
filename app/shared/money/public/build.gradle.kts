import build.wallet.gradle.logic.extensions.targets
import build.wallet.gradle.logic.gradle.exclude

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.coroutines.native)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.amountPublic)
        api(projects.shared.f8ePublic)
        api(projects.shared.featureFlagPublic)
        api(projects.shared.dbResultPublic)
        implementation(libs.kmp.big.number)
        implementation(libs.kmp.kotlin.codepoints)
        implementation(libs.kmp.kotlin.datetime)
        implementation(libs.kmp.kotlin.serialization.core)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.moneyTesting) {
          exclude(projects.shared.moneyPublic)
        }
      }
    }
  }
}
