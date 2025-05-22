import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain.dependencies {
      implementation(projects.domain.txVerificationPublic)
      implementation(projects.domain.databasePublic)
    }
    commonTest.dependencies {
      implementation(projects.libs.sqldelightFake)
      implementation(projects.libs.timeFake)
      implementation(projects.libs.testingPublic)
    }
  }
}
