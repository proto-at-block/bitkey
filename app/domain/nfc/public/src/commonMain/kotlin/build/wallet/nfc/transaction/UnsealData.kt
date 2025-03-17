package build.wallet.nfc.transaction

import build.wallet.crypto.SealedData
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.transaction.UnsealData.UnsealedDataResult
import okio.ByteString

class UnsealData(
  private val sealedData: SealedData,
  private val success: suspend (UnsealedDataResult) -> Unit,
  private val failure: () -> Unit,
  override val needsAuthentication: Boolean = true,
  override val shouldLock: Boolean = false,
) : NfcTransaction<UnsealedDataResult> {
  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ) = UnsealedDataResult(
    unsealedData = commands.unsealData(session, sealedData)
  )

  override suspend fun onSuccess(response: UnsealedDataResult) = success(response)

  override fun onCancel() = failure()

  data class UnsealedDataResult(
    val unsealedData: ByteString,
  )
}
