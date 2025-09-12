package build.wallet.nfc

import build.wallet.nfc.platform.NfcCommands

/**
 * Overrides NFC commands for the W3 and delegates unchanged commands to
 * an existing implementation otherwise.
 */
class BitkeyW3Commands(
  delegate: NfcCommands,
) : NfcCommands by delegate
