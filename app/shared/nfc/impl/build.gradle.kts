import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountPublic)
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.datadogPublic)
        api(projects.shared.keyValueStorePublic)
        implementation(libs.kmp.okio)
        implementation(projects.shared.stdlibPublic)
        implementation(libs.kmp.settings)
      }
    }
    val commonJvmMain by getting {
      dependencies {
        implementation(projects.shared.authPublic)
        implementation(projects.shared.nfcFake)
      }
    }
    val jvmMain by getting {
      dependencies {
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(libs.android.datadog.logs)
        implementation(projects.rust.firmwareFfi)
        implementation(projects.shared.memfaultPublic)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.cloudBackupFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.nfcFake)
        implementation(projects.shared.platformFake)
        // TODO: extract reusable uuid() - https://github.com/squareup/wallet/pull/13871
        implementation(projects.shared.platformImpl)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.firmwareFake)
      }
    }
    val commonJvmTest by getting {
      dependencies {
        implementation(projects.shared.bdkBindingsImpl)
        implementation(projects.shared.encryptionImpl)
        implementation(projects.shared.keyValueStoreFake)
      }
    }
  }
}
