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
        api(projects.domain.cloudBackupPublic)
        api(projects.libs.loggingPublic)
        api(projects.libs.platformPublic)
        api(projects.libs.grantsPublic)
        api(projects.domain.featureFlagPublic)
      }
    }
  }
}
