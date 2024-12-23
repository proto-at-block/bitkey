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
        api(projects.shared.bitcoinPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.fwupPublic)
        api(projects.shared.queueProcessorPublic)
        api(projects.shared.recoveryPublic)
        api(projects.shared.inheritancePublic)
        api(projects.shared.relationshipsPublic)

        implementation(projects.shared.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.workerFake)
      }
    }
  }
}
