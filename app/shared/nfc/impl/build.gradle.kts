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
      }
    }
    val commonJvmMain by getting {
      dependencies {
        implementation(projects.shared.authPublic)
        implementation(projects.shared.nfcFake)
        implementation(libs.kmp.secp256k1)
      }
    }
    val jvmMain by getting {
      dependencies {
        implementation(libs.jvm.secp256k1)
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(libs.android.secp256k1)
        implementation(libs.android.datadog.logs)
        implementation(projects.core)
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
        implementation(projects.shared.testingPublic)
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
