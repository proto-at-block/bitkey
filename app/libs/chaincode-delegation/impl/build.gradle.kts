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
        implementation(projects.libs.stdlibPublic)
        implementation(projects.libs.chaincodeDelegationPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.domain.walletPublic)
      }
    }

    jvmMain {
      dependencies {
        implementation(projects.rust.coreFfi)
        implementation(projects.domain.walletImpl)
      }
    }

    androidMain {
      dependencies {
        implementation(projects.rust.coreFfi)
        implementation(projects.domain.walletImpl)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.chaincodeDelegationFake)
        implementation(projects.domain.walletFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
      }
    }
  }
}
