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
        api(projects.domain.databasePublic)
        api(projects.domain.debugPublic)
        api(projects.domain.f8eClientPublic)
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
        implementation(projects.shared.workerFake)
      }
    }
  }
}
