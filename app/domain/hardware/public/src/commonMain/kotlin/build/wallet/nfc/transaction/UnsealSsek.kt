package build.wallet.nfc.transaction

import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Ssek
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands

/**
 * NFC transaction that unseals a SSEK using hardware.
 * Returns the unsealed SSEK that can be used for encryption/decryption operations.
 */
class UnsealSsek(
  private val sealedSsek: SealedSsek,
  private val success: suspend (Ssek) -> Unit,
  private val failure: () -> Unit,
  override val needsAuthentication: Boolean = true,
  override val shouldLock: Boolean = false,
) : NfcTransaction<Ssek> {
  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ): Ssek {
    val unsealedSsekBytes = commands.unsealData(session, sealedSsek)
    return Ssek(SymmetricKeyImpl(raw = unsealedSsekBytes))
  }

  override suspend fun onSuccess(response: Ssek) = success(response)

  override fun onCancel() = failure()
}
