package build.wallet.nfc

import android.content.Context
import android.nfc.NfcAdapter

class AndroidNfcAdapterProvider(
  context: Context,
) : NfcAdapterProvider {
  override val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
}
