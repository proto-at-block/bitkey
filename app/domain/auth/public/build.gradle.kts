import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        api(projects.libs.encryptionPublic)
        api(projects.domain.bitkeyPrimitivesPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.libs.loggingPublic)
      }
    }
  }
}
