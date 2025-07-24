package build.wallet.nfc.transaction

import build.wallet.bitkey.challange.DelayNotifyChallenge
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Ssek
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.transaction.SignChallengeAndSealSeks.SignedChallengeAndSeks

class SignChallengeAndSealSeks(
  private val challenge: DelayNotifyChallenge,
  private val csek: Csek,
  private val ssek: Ssek,
  private val success: suspend (SignedChallengeAndSeks) -> Unit,
  private val failure: () -> Unit,
  override val needsAuthentication: Boolean = true,
  // Don't lock because we quickly call [SignChallenge] next to get HW PoP
  override val shouldLock: Boolean = false,
) : NfcTransaction<SignedChallengeAndSeks> {
  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ) = SignedChallengeAndSeks(
    signedChallenge = HardwareSignedChallenge(
      challenge = challenge,
      signature = commands.signChallenge(session, challenge.asByteString())
    ),
    sealedCsek = commands.sealData(session, csek.key.raw),
    sealedSsek = commands.sealData(session, ssek.key.raw)
  )

  override suspend fun onSuccess(response: SignedChallengeAndSeks) = success(response)

  override fun onCancel() = failure()

  data class SignedChallengeAndSeks(
    val signedChallenge: HardwareSignedChallenge,
    val sealedCsek: SealedCsek,
    val sealedSsek: SealedSsek,
  )
}
