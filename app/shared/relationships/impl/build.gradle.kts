
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.shared.accountPublic)
        implementation(projects.shared.databasePublic)
        implementation(projects.shared.f8eClientPublic)
        implementation(projects.shared.coroutinesPublic)
        implementation(projects.shared.serializationPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.debugFake)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.f8eClientImpl)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.ktorClientFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.f8eClientImpl)
        implementation(projects.shared.encryptionFake)
        implementation(projects.shared.analyticsFake)
      }
    }

    jvmTest {
      dependencies {
        implementation(projects.shared.authImpl)
        implementation(projects.shared.encryptionFake)
        implementation(projects.shared.encryptionImpl)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.sqldelightTesting)
      }
    }
  }
}
