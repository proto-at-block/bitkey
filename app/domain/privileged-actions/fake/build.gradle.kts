import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)
  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.privilegedActionsPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.domain.walletPublic)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.accountFake)
      }
    }
  }
}
