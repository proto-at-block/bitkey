import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}
kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.bugsnagPublic)
        api(projects.libs.datadogPublic)
        api(projects.libs.amountPublic)
        api(projects.domain.accountPublic)
        api(projects.domain.analyticsPublic)
        api(projects.libs.bdkBindingsPublic)
        api(projects.domain.bitcoinPublic)
        api(projects.domain.bootstrapPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.domain.databasePublic)
        api(projects.domain.f8eClientPublic)
        api(projects.domain.inheritancePublic)
        api(projects.libs.keyValueStorePublic)
        api(projects.libs.loggingPublic)
        api(projects.libs.memfaultPublic)
        api(projects.libs.moneyPublic)
        api(projects.domain.nfcPublic)
        api(projects.libs.platformPublic)
        api(projects.domain.mobilePayPublic)
        api(projects.libs.queueProcessorPublic)
        api(projects.ui.featuresPublic)
        api(projects.libs.sqldelightPublic)
        api(projects.libs.timePublic)
        api(projects.domain.fwupPublic)
        api(projects.domain.inAppSecurityPublic)
        api(projects.domain.relationshipsPublic)
      }
    }

    iosMain {
      dependencies {
        api(projects.ui.composeAppControllerPublic)
      }
    }
  }
}
