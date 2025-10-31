import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.analyticsPublic)
        api(projects.domain.bitkeyPrimitivesFake)
        api(projects.libs.ktorClientFake)
        api(projects.domain.notificationsFake)
        api(projects.libs.timeFake)
        api(projects.libs.platformFake)
        api(projects.domain.f8eClientPublic)
        implementation(projects.libs.encryptionFake)
        implementation(libs.kmp.test.kotest.assertions)
        implementation(projects.domain.txVerificationFake)
      }
    }
  }
}
