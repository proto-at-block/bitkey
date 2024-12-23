package build.wallet.nfc

import android.app.Application
import android.nfc.NfcAdapter
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class AndroidNfcAdapterProvider(
  application: Application,
) : NfcAdapterProvider {
  override val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(application)
}
