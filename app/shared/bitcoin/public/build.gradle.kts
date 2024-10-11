import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.coroutines.native)
  alias(libs.plugins.kotlin.serialization)
  id("build.wallet.redacted")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.datetime)
        api(libs.kmp.kotlin.serialization.core)
        api(projects.shared.bdkBindingsPublic)
        api(projects.shared.bitcoinPrimitivesPublic)
        api(projects.shared.bitkeyPrimitivesPublic)
        api(projects.shared.dbResultPublic)
        api(projects.shared.encryptionPublic)
        api(projects.shared.f8ePublic)
        api(projects.shared.featureFlagPublic)
        api(projects.shared.ktorClientPublic)
        api(projects.shared.moneyPublic)
        api(projects.shared.platformPublic)
        implementation(projects.shared.loggingPublic)
        implementation(projects.shared.serializationPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.bdkBindingsFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.timeFake)
      }
    }
  }
}
