import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
}

kotlin {
  targets(ios = true, jvm = true)
}
