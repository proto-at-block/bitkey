package build.wallet.nfc.transaction

import bitkey.auth.AccessToken
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Ssek
import build.wallet.crypto.SealedData
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.nfc.transaction.RecoveryProofAndKeyTransferLostApp.ProofAndKeyTransferLostAppResult
import okio.ByteString

/**
 * NFC transaction for Lost App recovery tap 2:
 * 1. Unseal DDK data (if present)
 * 2. Sign access token for PoP
 * 3. Unseal old SSEK (if present)
 */
class RecoveryProofAndKeyTransferLostApp(
  private val accessToken: AccessToken,
  private val sealedDdkData: SealedData?,
  private val sealedSsekForDecryption: SealedSsek?,
  private val success: suspend (ProofAndKeyTransferLostAppResult) -> Unit,
  private val failure: () -> Unit,
  override val needsAuthentication: Boolean = true,
  override val shouldLock: Boolean = false,
) : NfcTransaction<ProofAndKeyTransferLostAppResult> {
  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ): ProofAndKeyTransferLostAppResult {
    // 1. Unseal DDK data if present — catch failures so the session can continue
    //    and the caller can route to the DDK error screen instead of a generic NFC error.
    var ddkUnsealFailed = false
    val unsealedDdkData: ByteString? = sealedDdkData?.let {
      @Suppress("TooGenericExceptionCaught")
      try {
        commands.unsealData(session, it)
      } catch (e: Exception) {
        ddkUnsealFailed = true
        logWarn(throwable = e) { "Failed to unseal DDK data during recovery NFC session" }
        null
      }
    }

    // 2. Sign access token for hardware proof of possession
    val signature = commands.signAccessToken(session, accessToken)
    val hwProofOfPossession = HwFactorProofOfPossession(signature)

    // 3. Unseal old SSEK if present
    val unsealedOldSsek: Ssek? = sealedSsekForDecryption?.let {
      Ssek(commands.unsealSymmetricKey(session, it))
    }

    return ProofAndKeyTransferLostAppResult(
      hwProofOfPossession = hwProofOfPossession,
      unsealedDdkData = unsealedDdkData,
      unsealedOldSsek = unsealedOldSsek,
      ddkUnsealFailed = ddkUnsealFailed
    )
  }

  override suspend fun onSuccess(response: ProofAndKeyTransferLostAppResult) = success(response)

  override fun onCancel() = failure()

  data class ProofAndKeyTransferLostAppResult(
    val hwProofOfPossession: HwFactorProofOfPossession,
    val unsealedDdkData: ByteString?,
    val unsealedOldSsek: Ssek?,
    val ddkUnsealFailed: Boolean,
  )
}
