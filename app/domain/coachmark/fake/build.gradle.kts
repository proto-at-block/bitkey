import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.test.turbine)
        implementation(projects.domain.coachmarkImpl)
      }
    }
  }
}
