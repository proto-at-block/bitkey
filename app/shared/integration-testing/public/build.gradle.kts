import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.jvm.test.toxiproxy.client)
        api(projects.shared.appComponentImpl)
        api(projects.shared.devTreasuryPublic)
        api(projects.shared.nfcFake)
        api(projects.shared.testingPublic)
        api(projects.shared.timeFake)
        api(projects.shared.datadogPublic)
        implementation(projects.shared.cloudStoreFake)
        implementation(projects.shared.moneyTesting)
        implementation(projects.shared.resultPublic)
        implementation(projects.shared.stdlibPublic)
        implementation(projects.shared.moneyFake)
      }
    }

    jvmMain {
      dependencies {
        implementation(projects.shared.moneyFake)
      }
    }
  }
}
