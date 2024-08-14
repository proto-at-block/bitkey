import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(ios = true, jvm = true, android = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.shared.uiCorePublic)
        implementation(projects.shared.analyticsPublic)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.components.resources)
        implementation(libs.kmp.compottie)
        implementation(libs.kmp.compottie.resources)
      }
    }

    jvmTest {
      dependencies {
        implementation(compose.preview)
      }
    }
  }
}
