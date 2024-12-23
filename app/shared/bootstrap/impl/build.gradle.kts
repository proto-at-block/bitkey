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
        api(projects.shared.accountPublic)
        api(projects.shared.featureFlagPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.authFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.featureFlagFake)
      }
    }
  }
}
