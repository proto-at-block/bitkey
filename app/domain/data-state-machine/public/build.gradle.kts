import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.composeRuntimePublic)

        api(projects.domain.accountPublic)
        api(projects.domain.authPublic)
        api(projects.domain.bitcoinPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.domain.debugPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.domain.firmwarePublic)
        api(projects.domain.fwupPublic)
        api(projects.domain.homePublic)
        api(projects.domain.nfcPublic)
        api(projects.domain.notificationsPublic)
        api(projects.libs.contactMethodPublic)
        api(projects.domain.onboardingPublic)
        api(projects.domain.recoveryPublic)
        api(projects.libs.stateMachinePublic)
        api(projects.domain.emergencyAccessKitPublic)
      }
    }
  }
}
