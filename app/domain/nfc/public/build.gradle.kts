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
        api(projects.domain.authPublic)
        api(projects.domain.bitcoinPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.libs.loggingPublic)
        api(projects.libs.platformPublic)
        api(projects.domain.featureFlagPublic)
        api(projects.domain.firmwarePublic)
        api(projects.domain.fwupPublic)
      }
    }
  }
}
