import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain.dependencies {
      implementation(projects.domain.txVerificationPublic)
      implementation(projects.domain.databasePublic)
    }

    commonTest.dependencies {
      implementation(projects.libs.sqldelightFake)
      implementation(projects.libs.timeFake)
      implementation(projects.libs.testingPublic)
      implementation(projects.domain.txVerificationFake)
      implementation(projects.domain.f8eClientFake)
      implementation(projects.domain.accountFake)
      implementation(projects.domain.featureFlagFake)
      implementation(projects.libs.moneyFake)
      implementation(projects.libs.sqldelightTesting)
    }
  }
}
