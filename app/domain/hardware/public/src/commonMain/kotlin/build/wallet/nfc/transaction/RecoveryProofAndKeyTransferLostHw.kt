package build.wallet.nfc.transaction

import bitkey.auth.AccessToken
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.crypto.SealedData
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.transaction.RecoveryProofAndKeyTransferLostHw.ProofAndKeyTransferLostHwResult

/**
 * NFC transaction for Lost HW recovery tap 2:
 * 1. Sign access token for PoP
 * 2. Seal DDK private key (if present)
 */
class RecoveryProofAndKeyTransferLostHw(
  private val accessToken: AccessToken,
  private val ddkKeypair: AppKey<DelegatedDecryptionKey>?,
  private val success: suspend (ProofAndKeyTransferLostHwResult) -> Unit,
  private val failure: () -> Unit,
  override val needsAuthentication: Boolean = true,
  // Lock after this session — only network-only steps follow for Lost HW
  override val shouldLock: Boolean = true,
) : NfcTransaction<ProofAndKeyTransferLostHwResult> {
  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ): ProofAndKeyTransferLostHwResult {
    // 1. Sign access token for hardware proof of possession
    val signature = commands.signAccessToken(session, accessToken)
    val hwProofOfPossession = HwFactorProofOfPossession(signature)

    // 2. Seal DDK private key if present
    val sealedDdkData: SealedData? = ddkKeypair?.let {
      commands.sealData(session, it.privateKey.bytes)
    }

    return ProofAndKeyTransferLostHwResult(
      hwProofOfPossession = hwProofOfPossession,
      sealedDdkData = sealedDdkData
    )
  }

  override suspend fun onSuccess(response: ProofAndKeyTransferLostHwResult) = success(response)

  override fun onCancel() = failure()

  data class ProofAndKeyTransferLostHwResult(
    val hwProofOfPossession: HwFactorProofOfPossession,
    val sealedDdkData: SealedData?,
  )
}
