import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

compose {
  resources {
    publicResClass = true
  }
}

kotlin {
  targets(ios = true, jvm = true, android = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.analyticsPublic)
        api(projects.shared.composeRuntimePublic)
        api(projects.shared.coachmarkPublic)
        api(compose.runtime)
        api(compose.foundation)
        api(compose.material)
        api(compose.material3)
        api(compose.components.resources)
        implementation(compose.components.uiToolingPreview)
        implementation(libs.kmp.compottie)
        implementation(libs.kmp.compottie.resources)
        implementation(projects.shared.featureFlagPublic)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.android.compose.ui.activity)
        implementation(libs.android.io.coil.compose)
        implementation(libs.android.io.coil.svg)
      }
    }
  }
}
