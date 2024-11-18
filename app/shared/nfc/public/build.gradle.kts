import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.authPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.featureFlagPublic)
        api(projects.shared.firmwarePublic)
        api(projects.shared.fwupPublic)
      }
    }
  }
}
