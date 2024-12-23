import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.databasePublic)
        api(projects.shared.debugPublic)
        implementation(projects.shared.frostPublic)
        implementation(projects.shared.loggingPublic)
        implementation(projects.shared.nfcFake)
        implementation(projects.shared.serializationPublic)
        implementation(libs.kmp.settings)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.authFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.bitcoinPublic)
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
