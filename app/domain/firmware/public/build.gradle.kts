import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.timePublic)
        api(libs.kmp.okio)
        api(projects.libs.memfaultPublic)
        api(projects.domain.featureFlagPublic)
        api(projects.libs.queueProcessorPublic)
      }
    }
  }
}
