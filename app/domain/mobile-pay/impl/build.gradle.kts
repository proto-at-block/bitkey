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
        implementation(libs.kmp.kotlin.datetime)
        implementation(libs.kmp.okio)
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.debugPublic)
        implementation(projects.libs.moneyPublic)
        implementation(projects.libs.ktorClientPublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.libs.chaincodeDelegationPublic)
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
        implementation(projects.domain.txVerificationFake)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.timeFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.chaincodeDelegationFake)
      }
    }
  }
}
