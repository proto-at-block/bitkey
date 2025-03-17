import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.accountPublic)
        api(projects.domain.authPublic)
        api(projects.domain.bitcoinPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.domain.featureFlagPublic)
        api(projects.libs.ktorClientPublic)
        api(projects.libs.timePublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(projects.domain.bitcoinFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
      }
    }
  }
}
