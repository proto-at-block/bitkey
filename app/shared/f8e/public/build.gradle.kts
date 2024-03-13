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
        implementation(projects.shared.platformPublic)
        implementation(projects.shared.serializationPublic)
        implementation(projects.shared.encryptionPublic)
        implementation(libs.kmp.ktor.client.core)
        api(projects.shared.ktorClientPublic)
        api(projects.shared.loggingPublic)
      }
    }
  }
}
