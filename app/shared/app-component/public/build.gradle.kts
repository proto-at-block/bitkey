import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}
kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.amountPublic)
        api(projects.shared.bugsnagPublic)
        api(projects.shared.accountPublic)
        api(projects.shared.analyticsPublic)
        api(projects.shared.coroutinesPublic)
        api(projects.shared.bdkBindingsPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.bootstrapPublic)
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.datadogPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.memfaultPublic)
        api(projects.shared.moneyPublic)
        api(projects.shared.nfcPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.mobilePayPublic)
        api(projects.shared.queueProcessorPublic)
        api(projects.shared.stateMachineUiPublic)
        api(projects.shared.sqldelightPublic)
        api(projects.shared.timePublic)
        api(projects.shared.fwupPublic)
        api(projects.shared.inAppSecurityPublic)
      }
    }
  }
}
