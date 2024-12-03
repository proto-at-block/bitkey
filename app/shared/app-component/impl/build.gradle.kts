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
        api(projects.shared.bootstrapImpl)
        api(projects.shared.bugsnagImpl)
        api(projects.shared.coachmarkImpl)
        api(projects.shared.cloudBackupImpl)
        api(projects.shared.cloudStoreImpl)
        api(projects.shared.datadogImpl)
        api(projects.shared.debugImpl)
        api(projects.shared.emergencyAccessKitImpl)
        api(projects.shared.emergencyAccessKitFake)
        api(projects.shared.encryptionImpl)
        api(projects.shared.f8ePublic)
        api(projects.shared.f8eClientImpl)
        api(projects.shared.featureFlagImpl)
        api(projects.shared.firmwareImpl)
        api(projects.shared.firmwareFake)
        api(projects.shared.frostImpl)
        api(projects.shared.fwupImpl)
        api(projects.shared.homeImpl)
        api(projects.shared.keyValueStoreImpl)
        api(projects.shared.keyboxImpl)
        api(projects.shared.loggingImpl)
        api(projects.shared.memfaultImpl)
        api(projects.shared.mobilePayImpl)
        api(projects.shared.moneyImpl)
        api(projects.shared.nfcImpl)
        api(projects.shared.notificationsImpl)
        api(projects.shared.onboardingImpl)
        api(projects.shared.partnershipsImpl)
        api(projects.shared.contactMethodImpl)
        api(projects.shared.platformImpl)
        api(projects.shared.queueProcessorImpl)
        api(projects.shared.recoveryImpl)
        api(projects.shared.sqldelightImpl)
        api(projects.shared.stateMachineDataImpl)
        api(projects.shared.stateMachineUiPublic)
        api(projects.shared.timeImpl)
        api(projects.shared.supportImpl)
        api(projects.shared.workerImpl)
        api(projects.shared.inAppSecurityImpl)
        api(projects.shared.priceChartImpl)
        api(projects.shared.inheritanceImpl)
        api(projects.shared.relationshipsImpl)
      }
    }

    val androidMain by getting {
      dependencies {
        api(projects.shared.googleSignInImpl)
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(projects.shared.f8eClientFake)
      }
    }
  }
}
