import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

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
