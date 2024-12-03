import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  id("build.wallet.redacted")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      /**
       * Note! This is a primitives module,
       * should contain absolute *minimum* amount of dependencies!
       */
      dependencies {
        api(projects.shared.composeRuntimePublic)
        api(libs.kmp.kotlin.datetime)
        api(projects.shared.encryptionPublic)
        api(libs.kmp.okio)
        api(projects.shared.bitcoinPrimitivesPublic)
        api(projects.shared.f8ePublic)
        api(projects.shared.frostPublic)
        implementation(projects.shared.serializationPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.bitkeyPrimitivesFake)
      }
    }
  }
}
