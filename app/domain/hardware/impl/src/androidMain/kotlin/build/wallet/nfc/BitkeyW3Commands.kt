package build.wallet.nfc

import build.wallet.nfc.platform.NfcCommands

/**
 * W3-specific NFC commands that delegate to the base implementation.
 */
class BitkeyW3Commands(
  private val delegate: NfcCommands,
) : NfcCommands by delegate
