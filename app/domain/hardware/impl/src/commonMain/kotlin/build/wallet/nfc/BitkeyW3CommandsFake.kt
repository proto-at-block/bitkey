package build.wallet.nfc

import build.wallet.nfc.platform.NfcCommands

/**
 * Fake implementation of NFC commands for the W3.
 *
 * Unless explicitly overridden here this will delegate to the fake W1 commands.
 */
class BitkeyW3CommandsFake(
  w1CommandsFake: BitkeyW1CommandsFake,
) : NfcCommands by w1CommandsFake
