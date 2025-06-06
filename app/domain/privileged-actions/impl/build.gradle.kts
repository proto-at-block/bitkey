import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()
  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.privilegedActionsPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.domain.walletPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.f8eClientFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.accountFake)
      }
    }
  }
}
