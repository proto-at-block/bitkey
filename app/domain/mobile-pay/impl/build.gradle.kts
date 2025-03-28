import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.datetime)
        api(libs.kmp.okio)
        api(projects.domain.accountPublic)
        api(projects.domain.debugPublic)
        api(projects.libs.moneyPublic)
        api(projects.libs.ktorClientPublic)
        api(projects.domain.databasePublic)
        api(projects.domain.walletPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.debugFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.mobilePayFake)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.timeFake)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
