import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
  id("build.wallet.di")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.datadogPublic)
        api(projects.libs.memfaultPublic)
        api(projects.libs.queueProcessorPublic)
        // TODO: remove dependency on :impl
        implementation(projects.libs.queueProcessorImpl) {
          because("Depends on PeriodicProcessorImpl")
        }
        api(projects.domain.accountPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.domain.databasePublic)
        api(projects.libs.keyValueStorePublic)
        api(projects.domain.analyticsPublic)
        api(projects.domain.walletPublic)
        api(projects.libs.grantsPublic)
        implementation(libs.kmp.okio)
        implementation(projects.libs.stdlibPublic)
        implementation(libs.kmp.settings)
      }
    }
    val commonJvmMain by getting {
      dependencies {
        implementation(projects.domain.authPublic)
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
        implementation(projects.domain.hardwareFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.memfaultFake)
        // TODO: extract reusable uuid() - https://github.com/squareup/wallet/pull/13871
        implementation(projects.libs.platformImpl)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.domain.walletFake)
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
