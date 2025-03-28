package build.wallet.nfc

import android.nfc.NfcAdapter

interface NfcAdapterProvider {
  /**
   * Provider for an NFC adapter.
   * Returns [NfcAdapter] if the phone has an NFC controller, otherwise returns `null`.
   *
   * Present [NfcAdapter] does not guarantee that the NFC is enabled on the phone, use
   * [NfcAdapter.isEnabled] to determine if that's the case.
   */
  val nfcAdapter: NfcAdapter?
}
