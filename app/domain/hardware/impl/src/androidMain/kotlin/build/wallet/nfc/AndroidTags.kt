package build.wallet.nfc

import android.nfc.tech.IsoDep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Suspending version of [IsoDep.transceive] which ensures that I/O operation is performed
 * on [Dispatchers.NfcIO] dispatcher.
 */
suspend fun IsoDep.awaitTransceive(data: ByteString): ByteString =
  withContext(Dispatchers.NfcIO) {
    transceive(data.toByteArray()).toByteString()
  }
