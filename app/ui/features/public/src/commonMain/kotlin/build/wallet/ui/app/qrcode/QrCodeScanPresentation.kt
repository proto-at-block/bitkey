package build.wallet.ui.app.qrcode

internal expect val usesDynamicIslandQrScannerPortal: Boolean

internal fun isKnownDynamicIslandIPhoneModel(modelIdentifier: String): Boolean {
  return modelIdentifier in knownDynamicIslandIPhoneModelIdentifiers
}

private val knownDynamicIslandIPhoneModelIdentifiers =
  setOf(
    // iPhone 14 Pro, iPhone 14 Pro Max
    "iPhone15,2",
    "iPhone15,3",
    // iPhone 15, iPhone 15 Plus, iPhone 15 Pro, iPhone 15 Pro Max
    "iPhone15,4",
    "iPhone15,5",
    "iPhone16,1",
    "iPhone16,2",
    // iPhone 16, iPhone 16 Plus, iPhone 16 Pro, iPhone 16 Pro Max.
    // iPhone17,5 is iPhone 16e and intentionally excluded.
    "iPhone17,1",
    "iPhone17,2",
    "iPhone17,3",
    "iPhone17,4",
    // iPhone 17 Pro, iPhone 17 Pro Max, iPhone 17, iPhone Air.
    // iPhone18,5 is iPhone 17e and intentionally excluded.
    "iPhone18,1",
    "iPhone18,2",
    "iPhone18,3",
    "iPhone18,4"
  )
