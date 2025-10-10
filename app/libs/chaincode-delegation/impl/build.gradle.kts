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
      }
    }

    jvmMain {
      dependencies {
        implementation(projects.libs.chaincodeDelegationPublic)
        implementation(projects.rust.coreFfi)
        implementation(projects.domain.walletImpl)
      }
    }

    androidMain {
      dependencies {
        implementation(projects.libs.chaincodeDelegationPublic)
        implementation(projects.rust.coreFfi)
        implementation(projects.domain.walletImpl)
      }
    }
  }
}
