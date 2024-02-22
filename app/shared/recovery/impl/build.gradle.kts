import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.databasePublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.coroutinesPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.f8eClientImpl)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.ktorTestFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.f8eClientImpl)
        implementation(projects.shared.encryptionImpl)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
