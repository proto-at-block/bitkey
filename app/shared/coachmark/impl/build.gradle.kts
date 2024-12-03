import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.databasePublic)
        api(projects.shared.featureFlagPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.accountFake)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.timeFake)
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.coachmarkFake)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
