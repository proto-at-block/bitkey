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
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.domain.debugPublic)
        implementation(projects.domain.f8eClientPublic)
        // TODO: break impl dependency.
        implementation(projects.domain.f8eClientImpl)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.debugFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.timeFake)
        implementation(projects.domain.workerFake)
      }
    }
  }
}
