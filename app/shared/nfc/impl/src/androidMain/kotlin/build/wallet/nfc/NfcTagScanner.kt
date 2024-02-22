package build.wallet.nfc

import android.nfc.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Using [NfcTagScanner] requires an Activity's [Lifecycle] to register
 * [NfcTagScannerLifecycleObserver].
 */
interface NfcTagScanner {
  /**
   * Emits discovered [Tag]s.
   */
  val tags: Flow<Tag>
}
