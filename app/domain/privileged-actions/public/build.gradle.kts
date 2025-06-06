import build.wallet.gradle.logic.extensions.targets
plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.accountPublic)
        api(projects.domain.authPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.domain.walletPublic)
        api(projects.libs.grantsPublic)
        api(projects.libs.ktorClientPublic)
        api(projects.libs.timePublic)
        implementation(projects.libs.stdlibPublic)
      }
    }
  }
}
