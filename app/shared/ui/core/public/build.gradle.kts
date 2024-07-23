import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.analyticsPublic)
        api(projects.shared.composeRuntimePublic)
        api(projects.shared.coachmarkPublic)
        implementation(projects.shared.featureFlagPublic)
      }
    }
  }
}
