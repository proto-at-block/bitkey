import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets.commonMain.dependencies {
    implementation(projects.libs.loggingPublic)
    api(projects.libs.moneyPublic)
    api(projects.domain.walletPublic)
    api(projects.libs.sqldelightPublic)
  }
}
