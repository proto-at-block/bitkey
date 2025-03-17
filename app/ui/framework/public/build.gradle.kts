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
        api(projects.domain.analyticsPublic)
        api(projects.libs.composeRuntimePublic)
        api(projects.domain.coachmarkPublic)
        api(compose.runtime)
        api(compose.foundation)
        api(compose.material)
        api(compose.material3)
        api(compose.components.resources)
        implementation(libs.kmp.coil.compose)
        implementation(libs.kmp.coil.svg)
        implementation(libs.kmp.coil.ktor)
        implementation(libs.kmp.compottie)
        implementation(libs.kmp.compottie.resources)
        implementation(projects.domain.featureFlagPublic)
        implementation(projects.libs.loggingPublic)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.android.compose.ui.activity)
        implementation(libs.android.compose.ui.tooling)
        implementation(libs.android.compose.ui.tooling.preview)
      }
    }
  }
}
