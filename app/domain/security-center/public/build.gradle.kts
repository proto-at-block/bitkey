import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.domain.cloudBackupPublic)
        implementation(projects.domain.inAppSecurityPublic)
        implementation(projects.domain.inheritancePublic)
        implementation(projects.domain.recoveryPublic)
        implementation(projects.domain.notificationsPublic)
        implementation(projects.libs.loggingPublic)
      }
    }
  }
}
