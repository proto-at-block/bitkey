package build.wallet.statemachine.nfc

/**
 * Android-only states for handling NFC availability issues.
 * Used across multiple NFC state machines (signing, fwup, etc).
 */
sealed interface AndroidNfcAvailabilityUiState {
  /** NFC hardware not available on device */
  data object NoNFCMessage : AndroidNfcAvailabilityUiState

  /** NFC is disabled, show instructions to enable it */
  data object EnableNFCInstructions : AndroidNfcAvailabilityUiState

  /** Navigate to system settings to enable NFC */
  data object NavigateToEnableNFC : AndroidNfcAvailabilityUiState
}
