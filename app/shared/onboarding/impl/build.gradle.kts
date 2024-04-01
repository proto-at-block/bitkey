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
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.databasePublic)
        implementation(projects.shared.loggingPublic)
        implementation(projects.shared.serializationPublic)
        implementation(libs.kmp.settings)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.sqldelightTesting)
      }
    }
  }
}
