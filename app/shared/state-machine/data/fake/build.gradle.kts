import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.authFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.bdkBindingsFake)
        implementation(projects.shared.moneyFake)
        implementation(projects.shared.mobilePayFake)
        implementation(projects.shared.firmwareFake)
        implementation(projects.shared.fwupFake)
      }
    }
  }
}
