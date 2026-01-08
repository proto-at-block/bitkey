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
        api(projects.libs.datadogPublic)
        api(projects.shared.appComponentImpl)
        api(projects.libs.devTreasuryPublic)
        api(projects.libs.testingPublic)
        api(projects.libs.timeFake)
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.libs.moneyTesting)
        implementation(projects.libs.stdlibPublic)
        implementation(projects.libs.moneyFake)
      }
    }

    jvmMain {
      dependencies {
        implementation(projects.libs.moneyFake)
        implementation(projects.libs.stateMachineTesting)
        implementation(projects.domain.bitkeyPrimitivesFake)
      }
    }
  }
}
