import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.authFake)
        implementation(projects.domain.bitcoinFake)
        implementation(projects.libs.bdkBindingsFake)
        implementation(projects.domain.debugFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.domain.mobilePayFake)
        implementation(projects.domain.firmwareFake)
        implementation(projects.domain.fwupFake)
      }
    }
  }
}
