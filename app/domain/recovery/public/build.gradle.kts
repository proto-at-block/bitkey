import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.accountPublic)
        api(projects.domain.authPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.domain.featureFlagPublic)
        api(projects.domain.walletPublic)
        api(projects.libs.ktorClientPublic)
        api(projects.libs.timePublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.walletFake)
      }
    }
  }
}
