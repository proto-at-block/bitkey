import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.analyticsPublic)
        api(projects.shared.ktorClientPublic)
        api(projects.shared.availabilityPublic)
        api(projects.shared.authPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.notificationsPublic)
        api(projects.shared.mobilePayPublic)
        api(projects.shared.timePublic)

        api(libs.kmp.kotlin.result)
        api(libs.kmp.ktor.client.core)
      }
    }
  }
}
