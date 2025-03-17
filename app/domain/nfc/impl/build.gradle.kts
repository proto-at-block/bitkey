import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.datadogPublic)
        api(projects.domain.accountPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.libs.keyValueStorePublic)
        api(projects.domain.analyticsPublic)
        implementation(libs.kmp.okio)
        implementation(projects.libs.stdlibPublic)
        implementation(libs.kmp.settings)
      }
    }
    val commonJvmMain by getting {
      dependencies {
        implementation(projects.domain.authPublic)
        implementation(projects.domain.nfcFake)
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
        implementation(projects.libs.memfaultPublic)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.domain.nfcFake)
        implementation(projects.libs.platformFake)
        // TODO: extract reusable uuid() - https://github.com/squareup/wallet/pull/13871
        implementation(projects.libs.platformImpl)
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.firmwareFake)
      }
    }
    val commonJvmTest by getting {
      dependencies {
        implementation(projects.libs.bdkBindingsImpl)
        implementation(projects.libs.encryptionImpl)
        implementation(projects.libs.keyValueStoreFake)
      }
    }
  }
}
