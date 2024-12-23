package build.wallet.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.flowWithLifecycle
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import build.wallet.logging.NFC_TAG
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@BitkeyInject(ActivityScope::class)
class AndroidNfcTagScanner(
  nfcAdapterProvider: NfcAdapterProvider,
  private val activity: Activity,
  lifecycle: Lifecycle,
) : NfcTagScanner {
  private val nfcAdapter = nfcAdapterProvider.nfcAdapter

  /**
   * When [Activity] is at least in [State.RESUMED] state, enables NFC reader mode and emits scanned
   * tags. When [Activity] is below [State.STARTED] state, disables NFC reader mode.
   */
  override val tags: Flow<Tag> =
    callbackFlow {
      if (nfcAdapter != null && nfcAdapter.isEnabled) {
        nfcAdapter.enableReaderMode(
          activity,
          // TODO: this throws a (logged) IllegalStateException when nothing is waiting on tagChannel
          { tag -> channel.trySend(tag) },
          NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
          Bundle()
        )
        logDebug(tag = NFC_TAG) { "NFC enabled" }
      }

      awaitClose {
        nfcAdapter?.disableReaderMode(activity)
        logDebug(tag = NFC_TAG) { "NFC disabled" }
      }
    }.flowWithLifecycle(
      lifecycle = lifecycle,
      minActiveState = RESUMED
    )
}
