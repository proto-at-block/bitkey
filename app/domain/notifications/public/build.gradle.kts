import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kmp.kotlin.datetime)
        api(projects.domain.authPublic)
        api(projects.domain.bitcoinPublic)
        api(projects.domain.featureFlagPublic)
        api(projects.libs.contactMethodPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.libs.queueProcessorPublic)
      }
    }
  }
}
