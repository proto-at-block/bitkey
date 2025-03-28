package build.wallet.nfc.platform

import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession

/**
 * Provides [NfcSession] instances.
 *
 * One of the NFC platform-specific APIs.
 */
interface NfcSessionProvider {
  @Throws(NfcException::class)
  fun get(parameters: NfcSession.Parameters): NfcSession
}
