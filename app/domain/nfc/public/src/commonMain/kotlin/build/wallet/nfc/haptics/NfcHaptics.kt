package build.wallet.nfc.haptics

interface NfcHaptics {
  fun vibrateConnection()

  fun vibrateSuccess()

  fun vibrateFailure()
}
