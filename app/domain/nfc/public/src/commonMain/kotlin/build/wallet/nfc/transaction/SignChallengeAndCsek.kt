package build.wallet.nfc.transaction

import build.wallet.bitkey.challange.DelayNotifyChallenge
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.transaction.SignChallengeAndCsek.SignedChallengeAndCsek

class SignChallengeAndCsek(
  private val challenge: DelayNotifyChallenge,
  private val csek: Csek,
  private val success: suspend (SignedChallengeAndCsek) -> Unit,
  private val failure: () -> Unit,
  override val needsAuthentication: Boolean = true,
  // Don't lock because we quickly call [SignChallenge] next to get HW PoP
  override val shouldLock: Boolean = false,
) : NfcTransaction<SignedChallengeAndCsek> {
  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ) = SignedChallengeAndCsek(
    signedChallenge = HardwareSignedChallenge(
      challenge = challenge,
      signature = commands.signChallenge(session, challenge.asByteString())
    ),
    sealedCsek = commands.sealKey(session, csek)
  )

  override suspend fun onSuccess(response: SignedChallengeAndCsek) = success(response)

  override fun onCancel() = failure()

  data class SignedChallengeAndCsek(
    val signedChallenge: HardwareSignedChallenge,
    val sealedCsek: SealedCsek,
  )
}
