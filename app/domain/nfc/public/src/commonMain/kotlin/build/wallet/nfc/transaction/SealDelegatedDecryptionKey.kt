package build.wallet.nfc.transaction

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.crypto.SealedData
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.transaction.SealDelegatedDecryptionKey.SealedDataResult

class SealDelegatedDecryptionKey(
  private val unsealedKeypair: AppKey<DelegatedDecryptionKey>,
  private val success: suspend (SealedDataResult) -> Unit,
  private val failure: () -> Unit,
  override val needsAuthentication: Boolean = true,
  override val shouldLock: Boolean = false,
) : NfcTransaction<SealedDataResult> {
  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ) = SealedDataResult(
    sealedData = commands.sealData(session, unsealedKeypair.privateKey.bytes)
  )

  override suspend fun onSuccess(response: SealedDataResult) = success(response)

  override fun onCancel() = failure()

  data class SealedDataResult(
    val sealedData: SealedData,
  )
}
