package build.wallet.nfc

fun interface NfcReaderCapability {
  /** Determines availability of the NFC tag reading functionality on the given phone. */
  fun availability(isHardwareFake: Boolean): NfcAvailability
}

sealed class NfcAvailability {
  /** Phone supports NFC tag reading, which might be [Enabled] or [Disabled] by the customer. */
  sealed class Available : NfcAvailability() {
    /** NFC reading is enabled on the phone. */
    data object Enabled : Available()

    /**
     * NFC reading is disabled by the customer in the phone settings.
     * This is only possible on Android phones. There is no way to disable NFC on iOS devices.
     */
    data object Disabled : Available()
  }

  /** Phone does not support NFC tag reading. */
  data object NotAvailable : NfcAvailability()
}
