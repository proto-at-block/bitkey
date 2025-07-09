import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true, android = true)

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        api(projects.domain.bitkeyPrimitivesPublic)
        api(projects.ui.frameworkPublic)
        implementation(projects.libs.timePublic)
        implementation(compose.components.resources)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(libs.kmp.compottie)
        implementation(libs.kmp.compottie.resources)
        implementation(projects.domain.analyticsPublic)
        implementation(libs.kmp.kotlin.serialization.json)
        implementation(projects.libs.loggingPublic)
      }
    }

    jvmTest {
      dependencies {
        implementation(compose.preview)
      }
    }
  }
}
