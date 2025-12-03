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
        implementation(projects.libs.datadogPublic)
        implementation(projects.libs.memfaultPublic)
        implementation(projects.libs.queueProcessorPublic)
        // TODO: remove dependency on :impl
        implementation(projects.libs.queueProcessorImpl) {
          because("Depends on PeriodicProcessorImpl")
        }
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.cloudBackupPublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.libs.keyValueStorePublic)
        implementation(projects.domain.analyticsPublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.libs.grantsPublic)
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
        implementation(projects.domain.featureFlagFake)
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
