import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.composeRuntimePublic)

        api(projects.shared.accountPublic)
        api(projects.shared.authPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.emailPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.firmwarePublic)
        api(projects.shared.fwupPublic)
        api(projects.shared.homePublic)
        api(projects.shared.nfcPublic)
        api(projects.shared.notificationsPublic)
        api(projects.shared.phoneNumberPublic)
        api(projects.shared.onboardingPublic)
        api(projects.shared.recoveryPublic)
        api(projects.shared.stateMachineFrameworkPublic)
        api(projects.shared.emergencyAccessKitPublic)
      }
    }
  }
}
