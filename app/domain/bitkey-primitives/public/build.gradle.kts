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
        api(projects.libs.composeRuntimePublic)
        api(libs.kmp.kotlin.datetime)
        api(projects.libs.encryptionPublic)
        api(libs.kmp.okio)
        api(projects.libs.bitcoinPrimitivesPublic)
        api(projects.libs.contactMethodPublic)
        api(projects.libs.frostPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
        // Used for base64 encoder provided by ktor lib.
        // TODO: use kotlin stdlib base64 instead and remove this dep.
        implementation(libs.kmp.ktor.client.core)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.bitkeyPrimitivesFake)
      }
    }
  }
}
