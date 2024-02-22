plugins {
  id("build.wallet.android.lib")
  kotlin("android")
}

dependencies {
  api(libs.android.lifecycle.common)
  // .impl because NfcManagerModule depends on implementation details. Ideally we should move
  // NfcManagerModule to .app module or nfc/impl/androidMain source set.
  api(projects.shared.nfcImpl)
  api(projects.shared.bitcoinPublic)
  implementation(libs.android.lifecycle.runtime)
}
