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
        api(projects.domain.accountPublic)
        api(projects.domain.featureFlagPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.authFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.featureFlagFake)
      }
    }
  }
}
