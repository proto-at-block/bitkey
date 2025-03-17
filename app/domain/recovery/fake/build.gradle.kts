import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.authFake)
        api(projects.libs.bitcoinPrimitivesFake)
        api(projects.libs.timeFake)
        api(projects.domain.f8eClientFake)
        implementation(projects.domain.relationshipsFake)
        implementation(projects.domain.relationshipsPublic)
      }
    }
  }
}
