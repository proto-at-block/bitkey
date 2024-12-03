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
        api(projects.shared.authPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.featureFlagPublic)
        api(projects.shared.contactMethodPublic)
        api(projects.shared.f8ePublic)
        api(projects.shared.queueProcessorPublic)
      }
    }
  }
}
