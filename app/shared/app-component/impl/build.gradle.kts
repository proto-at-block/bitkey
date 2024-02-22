import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountImpl)
        api(projects.shared.amountImpl)
        api(projects.shared.analyticsImpl)
        api(projects.shared.availabilityImpl)
        api(projects.shared.authImpl)
        api(projects.shared.bdkBindingsImpl)
        api(projects.shared.bitcoinImpl)
        api(projects.shared.bugsnagImpl)
        api(projects.shared.cloudBackupImpl)
        api(projects.shared.cloudStoreImpl)
        api(projects.shared.datadogImpl)
        api(projects.shared.emailImpl)
        api(projects.shared.emergencyAccessKitImpl)
        api(projects.shared.emergencyAccessKitFake)
        api(projects.shared.encryptionImpl)
        api(projects.shared.f8eImpl)
        api(projects.shared.f8eClientImpl)
        api(projects.shared.featureFlagImpl)
        api(projects.shared.firmwareImpl)
        api(projects.shared.fwupImpl)
        api(projects.shared.homeImpl)
        api(projects.shared.keyValueStoreImpl)
        api(projects.shared.keyboxImpl)
        api(projects.shared.ldkBindingsFake)
        api(projects.shared.loggingImpl)
        api(projects.shared.memfaultImpl)
        api(projects.shared.mobilePayImpl)
        api(projects.shared.moneyImpl)
        api(projects.shared.nfcImpl)
        api(projects.shared.notificationsImpl)
        api(projects.shared.onboardingImpl)
        api(projects.shared.phoneNumberImpl)
        api(projects.shared.platformImpl)
        api(projects.shared.queueProcessorImpl)
        api(projects.shared.recoveryImpl)
        api(projects.shared.sqldelightImpl)
        api(projects.shared.stateMachineDataImpl)
        api(projects.shared.stateMachineUiPublic)
        api(projects.shared.timeImpl)
        api(projects.shared.supportImpl)
      }
    }

    val androidMain by getting {
      dependencies {
        api(projects.android.nfcImpl)
        api(projects.shared.googleSignInImpl)
      }
    }
  }
}
