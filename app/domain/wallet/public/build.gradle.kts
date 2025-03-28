import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
  id("build.wallet.redacted")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.datetime)
        api(projects.libs.bdkBindingsPublic)
        api(projects.libs.bitcoinPrimitivesPublic)
        api(projects.domain.bitkeyPrimitivesPublic)
        api(projects.libs.encryptionPublic)
        api(projects.domain.featureFlagPublic)
        api(projects.libs.ktorClientPublic)
        api(projects.libs.moneyPublic)
        api(projects.domain.partnershipsPublic)
        api(projects.libs.platformPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.bdkBindingsFake)
        implementation(projects.libs.timeFake)
        implementation(projects.domain.walletFake)
      }
    }
  }
}
