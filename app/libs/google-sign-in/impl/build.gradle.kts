import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(android = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.platformPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(compose.runtime)
        implementation(libs.android.compose.ui.activity)
        implementation(libs.android.google.play.services.coroutines)
      }
    }
  }
}
