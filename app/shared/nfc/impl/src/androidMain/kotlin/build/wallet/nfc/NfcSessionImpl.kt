package build.wallet.nfc

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import build.wallet.logging.LogLevel
import build.wallet.logging.NFC_TAG
import build.wallet.logging.log
import build.wallet.toByteString
import build.wallet.toUByteList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

@OptIn(ExperimentalStdlibApi::class)
class NfcSessionImpl(
  override val parameters: NfcSession.Parameters,
  private val nfcTagScanner: NfcTagScanner,
  appCoroutineScope: CoroutineScope,
) : NfcSession {
  private val job: Job
  private val isoDep = MutableStateFlow<IsoDep?>(null)

  init {
    job =
      appCoroutineScope.launch(Dispatchers.NfcIO) {
        nfcTagScanner.tags
          .map { IsoDep.get(it) }
          .combine(isoDep) { newTag, currentTag -> if (currentTag == null) newTag else null }
          .filterNotNull()
          .collectLatest { isoDep.value = openTag(it) }
      }
  }

  override suspend fun transceive(buffer: List<UByte>) =
    isoDep
      .filterNotNull()
      .map { it.awaitTransceive(buffer.toByteString()) }
      .catch { cause ->
        when (cause) {
          // Treat [SecurityException] the same as [TagLostException] since it occurs
          // when we try to use an outdated tag
          is SecurityException, is TagLostException -> {
            closeTag()
            throw NfcException.CanBeRetried.TagLost(cause = cause)
          }
          is IOException -> throw NfcException.CanBeRetried.TransceiveFailure(cause = cause)
          else -> throw cause
        }
      }.first()
      .toUByteList()

  override var message: String?
    get() = null
    set(_) {}

  override fun close() {
    job.cancel(message = "NFC session closed")
    try {
      isoDep.value?.close()
    } catch (e: IOException) {
      log(tag = NFC_TAG, throwable = e, level = LogLevel.Warn) { "NFC session close failed" }
    }
  }

  private fun openTag(newTag: IsoDep): IsoDep? {
    log(tag = NFC_TAG) { "Connecting to NFC tag: ${newTag.tagId()}" }
    try {
      newTag.connect()
    } catch (e: IOException) {
      log(tag = NFC_TAG, throwable = e, level = LogLevel.Warn) { "NFC connection failed" }
      return null
    } catch (e: SecurityException) {
      log(tag = NFC_TAG, throwable = e, level = LogLevel.Warn) { "NFC connection failed" }
      return null
    }
    parameters.onTagConnected()
    return newTag
  }

  private fun closeTag() {
    isoDep.update {
      if (it != null) {
        log(tag = NFC_TAG) { "Closing tag: ${it.tagId()}" }
        try {
          it.close()
        } catch (e: IOException) {
          log(tag = NFC_TAG, throwable = e, level = LogLevel.Warn) { "NFC tag close failed" }
        } catch (e: SecurityException) {
          log(tag = NFC_TAG, throwable = e, level = LogLevel.Warn) { "NFC tag close failed" }
        }
        parameters.onTagDisconnected()
      }
      null
    }
  }

  private fun IsoDep.tagId() = tag.id.toHexString(HexFormat.Default)
}
