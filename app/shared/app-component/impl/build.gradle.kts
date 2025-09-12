import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.accountImpl)
        api(projects.domain.analyticsImpl)
        api(projects.domain.authImpl)
        api(projects.domain.availabilityImpl)
        api(projects.domain.bootstrapImpl)
        api(projects.domain.cloudBackupImpl)
        api(projects.domain.coachmarkImpl)
        api(projects.domain.dataStateMachineImpl)
        api(projects.domain.debugImpl)
        api(projects.domain.emergencyExitKitImpl)
        api(projects.domain.f8eClientImpl)
        api(projects.domain.featureFlagImpl)
        api(projects.domain.hardwareFake)
        api(projects.domain.hardwareImpl)
        api(projects.domain.inAppSecurityImpl)
        api(projects.domain.inheritanceImpl)
        api(projects.domain.metricsImpl)
        api(projects.domain.mobilePayImpl)
        api(projects.domain.notificationsImpl)
        api(projects.domain.onboardingImpl)
        api(projects.domain.partnershipsImpl)
        api(projects.domain.recoveryImpl)
        api(projects.domain.relationshipsImpl)
        api(projects.domain.securityCenterImpl)
        api(projects.domain.supportImpl)
        api(projects.domain.txVerificationImpl)
        api(projects.domain.walletImpl)
        api(projects.domain.workerImpl)
        api(projects.libs.amountImpl)
        api(projects.libs.bdkBindingsImpl)
        api(projects.libs.bugsnagImpl)
        api(projects.libs.chaincodeDelegationImpl)
        api(projects.libs.cloudStoreImpl)
        api(projects.libs.contactMethodImpl)
        api(projects.libs.datadogImpl)
        api(projects.libs.encryptionImpl)
        api(projects.libs.frostImpl)
        api(projects.libs.keyValueStoreImpl)
        api(projects.libs.loggingImpl)
        api(projects.libs.memfaultImpl)
        api(projects.libs.moneyImpl)
        api(projects.libs.platformImpl)
        api(projects.libs.queueProcessorImpl)
        api(projects.libs.secureEnclaveImpl)
        api(projects.libs.sqldelightImpl)
        api(projects.libs.timeImpl)
        api(projects.ui.featuresPublic)
        api(projects.ui.frameworkImpl)
        api(projects.shared.priceChartImpl)
        api(projects.shared.balanceUtilsImpl)
        api(projects.domain.privilegedActionsImpl)
      }
    }

    iosMain {
      dependencies {
        implementation(projects.libs.loggingImpl)
        implementation(projects.ui.composeAppControllerImpl)
      }
    }

    val androidMain by getting {
      dependencies {
        api(projects.libs.googleSignInImpl)
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.domain.emergencyExitKitFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.secureEnclaveFake)
        implementation(projects.domain.recoveryFake)
      }
    }
  }
}
